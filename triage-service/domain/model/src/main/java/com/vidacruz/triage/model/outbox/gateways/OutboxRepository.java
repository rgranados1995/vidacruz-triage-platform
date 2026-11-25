package com.vidacruz.triage.model.outbox.gateways;

import com.vidacruz.triage.model.outbox.OutboxMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Pending events of the outbox. The events are written by the patient repository together with each change.
 */
public interface OutboxRepository {

    /**
     * Reserves up to {@code batchSize} pending events for {@code lease} and emits them in the order they were
     * written. Two replicas never receive the same event while its lease is valid.
     */
    Flux<OutboxMessage> claimPending(int batchSize, Duration lease);

    /** Marks the event as published: it is never claimed again. */
    Mono<Void> markPublished(UUID eventId);
}
