package com.vidacruz.triage.model.audit;

import com.vidacruz.triage.model.patient.PatientStatus;

/**
 * Changes of a patient that RN-04 requires to audit and RF-01 to RF-03 require to publish.
 */
public enum PatientEventType {
    REGISTERED,
    CONSULTATION_STARTED,
    DISCHARGED,
    ESCALATED;

    /** Event produced when a patient enters the given status. */
    public static PatientEventType ofTransitionTo(PatientStatus status) {
        return switch (status) {
            case REGISTERED -> REGISTERED;
            case IN_CONSULTATION -> CONSULTATION_STARTED;
            case DISCHARGED -> DISCHARGED;
        };
    }
}
