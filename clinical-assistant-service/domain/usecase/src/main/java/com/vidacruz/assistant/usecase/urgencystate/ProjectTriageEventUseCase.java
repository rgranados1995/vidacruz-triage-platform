package com.vidacruz.assistant.usecase.urgencystate;

import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.gateways.UrgencyStateRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * RF-04 part B: keeps the projection of the emergency room up to date with each event of the triage, and decides
 * whether the events have to be read again from the start.
 */
@RequiredArgsConstructor
public class ProjectTriageEventUseCase {

    private final UrgencyStateRepository urgencyStateRepository;

    /** Emits true when the event changed the projection, false when it was older than what is stored. */
    public Mono<Boolean> project(PatientSnapshot snapshot) {
        return urgencyStateRepository.apply(snapshot);
    }

    /** True when the projection is empty and has to be rebuilt from the first event of the topic. */
    public Mono<Boolean> needsRebuild() {
        return urgencyStateRepository.isInitialized().map(initialized -> !initialized);
    }

    /** Called once the consumer reads from the right position, so a later start does not rebuild again. */
    public Mono<Void> rebuildStarted() {
        return urgencyStateRepository.markInitialized();
    }
}
