package com.vidacruz.triage.model.alert.gateways;

import com.vidacruz.triage.model.alert.MedicalAlert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Storage of the escalation alerts. The alerts are created and resolved by the patient repository, in the same
 * transaction as the escalation and the change of status; here they are read, acknowledged and watched.
 */
public interface MedicalAlertRepository {

    /** Pending and acknowledged alerts, the most severe level first and then the oldest. */
    Flux<MedicalAlert> findActive();

    Mono<MedicalAlert> findById(long id);

    /**
     * Marks a pending alert as acknowledged.
     *
     * @return the stored alert, or empty when the alert does not exist or is not pending
     */
    Mono<MedicalAlert> acknowledge(long id, String acknowledgedBy, Instant at);

    /**
     * Every change of an alert after the subscription (created, acknowledged or resolved), with its state after the
     * change, from any replica of the service. Hot: changes made before subscribing are not replayed.
     */
    Flux<MedicalAlert> changes();
}
