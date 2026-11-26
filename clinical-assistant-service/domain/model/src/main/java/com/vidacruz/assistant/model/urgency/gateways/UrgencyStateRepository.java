package com.vidacruz.assistant.model.urgency.gateways;

import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.UrgencyState;
import reactor.core.publisher.Mono;

/**
 * The projection of the emergency room built from the events of the triage.
 */
public interface UrgencyStateRepository {

    /**
     * Applies the state of a patient atomically, unless a newer one is already stored.
     *
     * @return true when applied, false when it was older than the stored one and was ignored
     */
    Mono<Boolean> apply(PatientSnapshot snapshot);

    Mono<UrgencyState> current();

    /** False when the projection was never built, or its data was lost: the events must be read from the start. */
    Mono<Boolean> isInitialized();

    Mono<Void> markInitialized();
}
