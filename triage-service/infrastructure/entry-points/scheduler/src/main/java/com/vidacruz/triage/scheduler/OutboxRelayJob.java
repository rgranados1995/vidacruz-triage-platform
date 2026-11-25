package com.vidacruz.triage.scheduler;

import com.vidacruz.triage.usecase.publishpendingevents.PublishPendingEventsUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Outbox relay: publishes the pending events of the outbox every {@code outbox.poll-interval}.
 */
@Component
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxRelayJob extends PeriodicJob {

    private final PublishPendingEventsUseCase publishPendingEvents;

    public OutboxRelayJob(PublishPendingEventsUseCase publishPendingEvents, OutboxRelayProperties properties) {
        super("Outbox relay", properties.pollInterval());
        this.publishPendingEvents = publishPendingEvents;
    }

    @Override
    protected Mono<Long> runRound() {
        return publishPendingEvents.publishPending();
    }
}
