-- Transactional outbox: each patient change writes its event here in the same transaction, and the relay publishes
-- it to Kafka. claimed_until is the lease of the replica publishing it; attempts counts the claims.
CREATE TABLE outbox_event (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id      UUID        NOT NULL UNIQUE,
    aggregate_id  UUID        NOT NULL,
    event_type    VARCHAR(50) NOT NULL,
    payload       JSONB       NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL,
    claimed_until TIMESTAMPTZ,
    attempts      INTEGER     NOT NULL DEFAULT 0,
    published_at  TIMESTAMPTZ
);

-- Only the pending events are scanned by the relay
CREATE INDEX outbox_event_pending_idx ON outbox_event (id) WHERE published_at IS NULL;
