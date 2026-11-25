package com.vidacruz.triage.model.outbox;

import java.time.Duration;

/**
 * How the relay takes events from the outbox.
 *
 * @param batchSize maximum events claimed per round
 * @param lease     time a claimed event stays reserved for the replica that claimed it; after that another round
 *                  can claim it again, so an event is never lost when a replica dies while publishing
 */
public record OutboxRelayPolicy(int batchSize, Duration lease) {

    public OutboxRelayPolicy {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("The outbox batch size must be positive, but it is " + batchSize);
        }
        if (lease == null || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("The outbox lease must be a positive duration, but it is " + lease);
        }
    }
}
