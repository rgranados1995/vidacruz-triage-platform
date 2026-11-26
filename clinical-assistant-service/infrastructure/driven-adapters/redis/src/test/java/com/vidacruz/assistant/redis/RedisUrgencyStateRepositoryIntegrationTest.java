package com.vidacruz.assistant.redis;

import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.PatientStatus;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection against a real Redis: duplicates, late older events, discharges and the initialization mark.
 */
@Testcontainers
class RedisUrgencyStateRepositoryIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.12-alpine")
            .withExposedPorts(6379);

    private static final Instant T1 = Instant.parse("2026-11-26T10:00:00.000001Z");
    private static final Instant T2 = T1.plusSeconds(60);
    private static final Instant T3 = T1.plusSeconds(120);
    private static final Duration RETENTION = Duration.ofDays(7);

    private static LettuceConnectionFactory connections;
    private static ReactiveStringRedisTemplate redis;
    private RedisUrgencyStateRepository repository;

    @BeforeAll
    static void connect() {
        connections = factory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis = new ReactiveStringRedisTemplate(connections);
    }

    @AfterAll
    static void disconnect() {
        connections.destroy();
    }

    @BeforeEach
    void emptyProjection() throws Exception {
        StepVerifier.create(redis.execute(connection -> connection.serverCommands().flushAll()).then())
                .verifyComplete();
        repository = new RedisUrgencyStateRepository(redis, new UrgencyStateProperties(RETENTION));
    }

    @Test
    void shouldCountAPatientWhileActiveAndForgetItWhenDischarged() {
        var patient = UUID.randomUUID();

        apply(new PatientSnapshot(patient, TriageLevel.CRITICAL, PatientStatus.REGISTERED, T1), true);
        assertCounts(TriageLevel.CRITICAL, 1, T1);
        apply(new PatientSnapshot(patient, TriageLevel.CRITICAL, PatientStatus.IN_CONSULTATION, T2), true);
        assertCounts(TriageLevel.CRITICAL, 1, T2);
        apply(new PatientSnapshot(patient, TriageLevel.CRITICAL, PatientStatus.DISCHARGED, T3), true);
        assertCounts(TriageLevel.CRITICAL, 0, T3);

        // The discharged patient is remembered, but not forever
        StepVerifier.create(redis.getExpire(UrgencyKeys.patient(patient)))
                .assertNext(ttl -> assertThat(ttl).isPositive().isLessThanOrEqualTo(RETENTION))
                .verifyComplete();
    }

    @Test
    void shouldNotChangeAnythingWhenAnEventArrivesTwice() {
        var registered = new PatientSnapshot(UUID.randomUUID(), TriageLevel.URGENT, PatientStatus.REGISTERED, T1);

        apply(registered, true);
        apply(registered, true);

        assertCounts(TriageLevel.URGENT, 1, T1);
    }

    @Test
    void shouldIgnoreAnOlderEventThatArrivesLate() {
        var patient = UUID.randomUUID();
        apply(new PatientSnapshot(patient, TriageLevel.URGENT_2, PatientStatus.DISCHARGED, T2), true);

        apply(new PatientSnapshot(patient, TriageLevel.URGENT_2, PatientStatus.REGISTERED, T1), false);

        assertCounts(TriageLevel.URGENT_2, 0, T2);
    }

    @Test
    void shouldMoveAPatientWhoseLevelChanges() {
        var patient = UUID.randomUUID();
        apply(new PatientSnapshot(patient, TriageLevel.NON_URGENT, PatientStatus.REGISTERED, T1), true);

        apply(new PatientSnapshot(patient, TriageLevel.URGENT, PatientStatus.REGISTERED, T2), true);

        StepVerifier.create(repository.current())
                .assertNext(state -> {
                    assertThat(state.activeByLevel()).containsEntry(TriageLevel.NON_URGENT, 0L)
                            .containsEntry(TriageLevel.URGENT, 1L);
                    assertThat(state.totalActive()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void shouldKeepTheLatestUpdateTimeAndEveryLevelCount() {
        apply(new PatientSnapshot(UUID.randomUUID(), TriageLevel.CRITICAL, PatientStatus.REGISTERED, T3), true);
        apply(new PatientSnapshot(UUID.randomUUID(), TriageLevel.NON_URGENT_2, PatientStatus.REGISTERED, T1), true);
        apply(new PatientSnapshot(UUID.randomUUID(), TriageLevel.NON_URGENT_2, PatientStatus.IN_CONSULTATION, T2),
                true);

        StepVerifier.create(repository.current())
                .assertNext(state -> {
                    assertThat(state.activeByLevel()).containsEntry(TriageLevel.CRITICAL, 1L)
                            .containsEntry(TriageLevel.NON_URGENT_2, 2L).containsEntry(TriageLevel.URGENT, 0L);
                    assertThat(state.totalActive()).isEqualTo(3);
                    assertThat(state.updatedAt()).contains(T3);
                })
                .verifyComplete();
    }

    @Test
    void shouldReadAnEmptyProjectionAndItsInitializationMark() {
        StepVerifier.create(repository.current())
                .assertNext(state -> {
                    assertThat(state.totalActive()).isZero();
                    assertThat(state.updatedAt()).isEmpty();
                })
                .verifyComplete();
        StepVerifier.create(repository.isInitialized()).expectNext(Boolean.FALSE).verifyComplete();
        StepVerifier.create(repository.markInitialized()).verifyComplete();
        StepVerifier.create(repository.isInitialized()).expectNext(Boolean.TRUE).verifyComplete();
    }

    @Test
    void shouldReportTheStateUnavailableWhenRedisCannotBeReached() throws Exception {
        var unreachable = factory("localhost", 1);
        var repositoryWithoutRedis = new RedisUrgencyStateRepository(new ReactiveStringRedisTemplate(unreachable),
                new UrgencyStateProperties(RETENTION));
        try {
            StepVerifier.create(repositoryWithoutRedis.current())
                    .expectErrorMatches(RedisUrgencyStateRepositoryIntegrationTest::unavailable)
                    .verify(Duration.ofSeconds(10));
            StepVerifier.create(repositoryWithoutRedis.apply(new PatientSnapshot(UUID.randomUUID(),
                            TriageLevel.CRITICAL, PatientStatus.REGISTERED, T1)))
                    .expectErrorMatches(RedisUrgencyStateRepositoryIntegrationTest::unavailable)
                    .verify(Duration.ofSeconds(10));
            StepVerifier.create(repositoryWithoutRedis.isInitialized())
                    .expectErrorMatches(RedisUrgencyStateRepositoryIntegrationTest::unavailable)
                    .verify(Duration.ofSeconds(10));
            StepVerifier.create(repositoryWithoutRedis.markInitialized())
                    .expectErrorMatches(RedisUrgencyStateRepositoryIntegrationTest::unavailable)
                    .verify(Duration.ofSeconds(10));
        } finally {
            unreachable.destroy();
        }
    }

    private void apply(PatientSnapshot snapshot, boolean applied) {
        StepVerifier.create(repository.apply(snapshot)).expectNext(applied).verifyComplete();
    }

    private void assertCounts(TriageLevel level, long expected, Instant updatedAt) {
        StepVerifier.create(repository.current())
                .assertNext(state -> {
                    assertThat(state.activeByLevel()).containsEntry(level, expected);
                    assertThat(state.updatedAt()).contains(updatedAt);
                })
                .verifyComplete();
    }

    private static boolean unavailable(Throwable error) {
        return error instanceof BusinessException business
                && business.getError() == BusinessError.URGENCY_STATE_UNAVAILABLE && business.getCause() != null;
    }

    private static LettuceConnectionFactory factory(String host, int port) {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build());
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
