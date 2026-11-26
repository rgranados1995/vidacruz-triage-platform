package com.vidacruz.triage.model.alert;

/**
 * Life of an escalation alert (RF-06): it is pending until a doctor takes it, and it is resolved, on its own, as soon
 * as the patient enters the consultation or is discharged.
 */
public enum AlertStatus {

    PENDING,
    ACKNOWLEDGED,
    RESOLVED;

    /** Active alerts still need the attention of the medical team. */
    public boolean isActive() {
        return this != RESOLVED;
    }
}
