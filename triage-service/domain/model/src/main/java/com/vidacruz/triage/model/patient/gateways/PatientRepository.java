package com.vidacruz.triage.model.patient.gateways;

import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Storage of patients. Every write receives a {@link PatientChange} so the patient, its audit entry and its event are
 * stored in one transaction.
 */
public interface PatientRepository {

    /** Stores a new patient and its registration audit entry. */
    Mono<Patient> register(PatientChange registration);

    /**
     * Applies a change of status only if the stored patient still has {@link PatientChange#previousStatus()};
     * otherwise it fails with an invalid transition, so two concurrent changes cannot both win. Emits the patient as
     * stored, so flags changed meanwhile by others (the escalation) are not lost.
     */
    Mono<Patient> update(PatientChange change);

    /**
     * Applies an escalation only if the stored patient is still registered and not escalated (RN-03). Emits the
     * escalated patient, or nothing when another replica escalated it first or it already entered the consultation.
     */
    Mono<Patient> escalate(PatientChange escalation);

    /** Registered patients not escalated whose SLA passed before {@code now}, oldest first, at most {@code limit}. */
    Flux<Patient> findOverdue(Instant now, int limit);

    /** Empty when the patient does not exist. */
    Mono<Patient> findById(UUID id);
}
