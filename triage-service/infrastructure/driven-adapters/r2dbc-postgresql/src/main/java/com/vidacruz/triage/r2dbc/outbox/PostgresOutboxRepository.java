package com.vidacruz.triage.r2dbc.outbox;

import com.vidacruz.triage.model.outbox.OutboxMessage;
import com.vidacruz.triage.model.outbox.gateways.OutboxRepository;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

/**
 * Outbox in PostgreSQL. The claim and the mark are single statements in autocommit: the lease, not an open
 * transaction, protects a claimed event while it is being published.
 */
@Repository
@RequiredArgsConstructor
public class PostgresOutboxRepository implements OutboxRepository {

    private static final double MILLIS_PER_SECOND = 1000.0;

    private final DatabaseClient client;

    @Override
    public Flux<OutboxMessage> claimPending(int batchSize, Duration lease) {
        return client.sql(OutboxSql.CLAIM)
                .bind("leaseSeconds", lease.toMillis() / MILLIS_PER_SECOND)
                .bind("batchSize", batchSize)
                .map(PostgresOutboxRepository::toClaimed)
                .all()
                .sort(Comparator.comparingLong(ClaimedEvent::sequence))
                .map(ClaimedEvent::message);
    }

    @Override
    public Mono<Void> markPublished(UUID eventId) {
        return client.sql(OutboxSql.MARK_PUBLISHED)
                .bind("eventId", eventId)
                .fetch().rowsUpdated()
                .then();
    }

    private static ClaimedEvent toClaimed(Readable row) {
        var message = new OutboxMessage(
                row.get("event_id", UUID.class),
                String.valueOf(row.get("aggregate_id", UUID.class)),
                row.get("event_type", String.class),
                row.get("payload", Json.class).asString(),
                row.get("occurred_at", Instant.class));
        return new ClaimedEvent(row.get("id", Long.class), message);
    }

    /** RETURNING does not keep the order of the subquery, so the rows are sorted by their insertion sequence. */
    private record ClaimedEvent(long sequence, OutboxMessage message) {
    }
}
