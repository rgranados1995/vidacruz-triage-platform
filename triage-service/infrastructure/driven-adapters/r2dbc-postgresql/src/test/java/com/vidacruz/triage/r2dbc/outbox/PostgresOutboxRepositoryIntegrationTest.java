package com.vidacruz.triage.r2dbc.outbox;

import com.vidacruz.triage.model.outbox.OutboxMessage;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.codec.Json;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox claim against a real PostgreSQL: order, exclusivity between concurrent claims, lease expiry and marks.
 * Every test starts from an empty outbox.
 */
@Testcontainers
class PostgresOutboxRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.16-alpine");

    private static final Duration LONG_LEASE = Duration.ofMinutes(5);

    private static DatabaseClient client;
    private PostgresOutboxRepository repository;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        client = DatabaseClient.create(new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build()));
    }

    @BeforeEach
    void setUp() {
        StepVerifier.create(client.sql("DELETE FROM outbox_event").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();
        repository = new PostgresOutboxRepository(client);
    }

    @Test
    void shouldClaimThePendingEventsInTheOrderTheyWereWritten() {
        var written = insertEvents(5);

        StepVerifier.create(repository.claimPending(10, LONG_LEASE).map(OutboxMessage::eventId).collectList())
                .assertNext(claimed -> assertThat(claimed).containsExactlyElementsOf(written))
                .verifyComplete();
    }

    @Test
    void shouldReturnTheStoredMessageAsWritten() {
        var eventId = insertEvents(1).getFirst();

        StepVerifier.create(repository.claimPending(1, LONG_LEASE))
                .assertNext(message -> {
                    assertThat(message.eventId()).isEqualTo(eventId);
                    assertThat(message.type()).isEqualTo("paciente_registrado");
                    assertThat(message.payload()).contains("\"subject\"");
                    assertThat(message.occurredAt()).isNotNull();
                    assertThat(UUID.fromString(message.key())).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldRespectTheBatchSize() {
        insertEvents(5);

        StepVerifier.create(repository.claimPending(2, LONG_LEASE)).expectNextCount(2).verifyComplete();
        StepVerifier.create(repository.claimPending(10, LONG_LEASE)).expectNextCount(3).verifyComplete();
    }

    @Test
    void shouldNeverGiveTheSameEventToTwoConcurrentClaims() {
        var written = insertEvents(40);

        var claimed = Flux.merge(repository.claimPending(25, LONG_LEASE), repository.claimPending(25, LONG_LEASE))
                .map(OutboxMessage::eventId)
                .collectList();

        StepVerifier.create(claimed)
                .assertNext(ids -> assertThat(ids).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(written))
                .verifyComplete();
    }

    @Test
    void shouldClaimAgainAnEventWhoseLeaseExpired() {
        var eventId = insertEvents(1).getFirst();
        StepVerifier.create(repository.claimPending(10, Duration.ZERO)).expectNextCount(1).verifyComplete();

        StepVerifier.create(repository.claimPending(10, LONG_LEASE).map(OutboxMessage::eventId))
                .expectNext(eventId)
                .verifyComplete();
    }

    @Test
    void shouldNotClaimAnEventStillLeasedNorAPublishedOne() {
        var ids = insertEvents(2);
        StepVerifier.create(repository.claimPending(1, LONG_LEASE)).expectNextCount(1).verifyComplete();
        StepVerifier.create(repository.markPublished(ids.get(1))).verifyComplete();

        StepVerifier.create(repository.claimPending(10, Duration.ZERO)).verifyComplete();
    }

    private List<UUID> insertEvents(int count) {
        var ids = new ArrayList<UUID>();
        for (var index = 0; index < count; index++) {
            var eventId = UUID.randomUUID();
            StepVerifier.create(client.sql(OutboxSql.INSERT)
                            .bind("eventId", eventId)
                            .bind("aggregateId", UUID.randomUUID())
                            .bind("eventType", "paciente_registrado")
                            .bind("payload", Json.of("{\"subject\": \"" + eventId + "\"}"))
                            .bind("occurredAt", Instant.now())
                            .fetch().rowsUpdated())
                    .expectNext(1L)
                    .verifyComplete();
            ids.add(eventId);
        }
        return ids;
    }
}
