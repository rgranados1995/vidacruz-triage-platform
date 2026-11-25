package com.vidacruz.triage.model.patient.gateways;

import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Storage of patients. Every write receives a {@link PatientChange} so the patient and its audit entry are stored in
 * one transaction.
 */
public interface PatientRepository {

    /** Stores a new patient and its registration audit entry. */
    Mono<Patient> register(PatientChange registration);

    /**
     * Applies a change only if the stored patient still has {@link PatientChange#previousStatus()}; otherwise it
     * fails with an invalid transition, so two concurrent changes cannot both win.
     */
    Mono<Patient> update(PatientChange change);

    /** Empty when the patient does not exist. */
    Mono<Patient> findById(UUID id);
}
