package com.vidacruz.assistant.model.urgency;

/**
 * Clinical status of a patient, as the triage service publishes it.
 */
public enum PatientStatus {

    REGISTERED,
    IN_CONSULTATION,
    DISCHARGED;

    /** RF-04 part B: every patient not discharged is still in the emergency room. */
    public boolean isActive() {
        return this != DISCHARGED;
    }
}
