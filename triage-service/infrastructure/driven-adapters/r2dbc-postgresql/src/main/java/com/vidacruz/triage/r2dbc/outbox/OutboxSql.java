package com.vidacruz.triage.r2dbc.outbox;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * SQL of the outbox. The claim is one statement: it locks the pending rows with SKIP LOCKED (two replicas never take
 * the same row) and sets a lease with the database clock (the same clock for every replica).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OutboxSql {

    static final String INSERT = """
            INSERT INTO outbox_event (event_id, aggregate_id, event_type, payload, occurred_at, traceparent)
            VALUES (:eventId, :aggregateId, :eventType, :payload, :occurredAt, :traceparent)
            """;

    static final String CLAIM = """
            UPDATE outbox_event
               SET claimed_until = now() + make_interval(secs => :leaseSeconds),
                   attempts = attempts + 1
             WHERE id IN (SELECT id
                            FROM outbox_event
                           WHERE published_at IS NULL
                             AND (claimed_until IS NULL OR claimed_until < now())
                           ORDER BY id
                           LIMIT :batchSize
                             FOR UPDATE SKIP LOCKED)
            RETURNING id, event_id, aggregate_id, event_type, payload, occurred_at, traceparent
            """;

    static final String MARK_PUBLISHED = """
            UPDATE outbox_event
               SET published_at = now(),
                   claimed_until = NULL
             WHERE event_id = :eventId
            """;
}
