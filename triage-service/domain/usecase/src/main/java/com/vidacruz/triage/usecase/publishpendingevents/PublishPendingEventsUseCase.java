package com.vidacruz.triage.usecase.publishpendingevents;

import com.vidacruz.triage.model.outbox.OutboxRelayPolicy;
import com.vidacruz.triage.model.outbox.gateways.EventPublisher;
import com.vidacruz.triage.model.outbox.gateways.OutboxRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * One round of the outbox relay: publishes the pending events in the order they were written and marks each one
 * as published after the broker acknowledges it. The first failure ends the round, so a later event of the same
 * patient is never published before an earlier one; the unpublished events are retried when their lease expires.
 */
@RequiredArgsConstructor
public class PublishPendingEventsUseCase {

    private final OutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final OutboxRelayPolicy policy;

    /** Emits how many events were published in this round. */
    public Mono<Long> publishPending() {
        return outboxRepository.claimPending(policy.batchSize(), policy.lease())
                .concatMap(message -> eventPublisher.publish(message)
                        .then(Mono.defer(() -> outboxRepository.markPublished(message.eventId())))
                        .thenReturn(message))
                .count();
    }
}
