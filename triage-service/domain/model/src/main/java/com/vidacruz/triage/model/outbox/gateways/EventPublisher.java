package com.vidacruz.triage.model.outbox.gateways;

import com.vidacruz.triage.model.outbox.OutboxMessage;
import reactor.core.publisher.Mono;

/**
 * Message broker where the events of the triage are published.
 */
public interface EventPublisher {

    /** Completes when the broker acknowledged the event; fails otherwise. */
    Mono<Void> publish(OutboxMessage message);
}
