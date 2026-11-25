package com.vidacruz.triage.model.patient;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Clinical flow of RF-02: a registered patient enters the consultation or is discharged directly, and a patient in
 * consultation can only be discharged. A discharged patient never changes again.
 */
@Getter
@RequiredArgsConstructor
public enum PatientStatus {

    REGISTERED("registrado"),
    IN_CONSULTATION("en consulta"),
    DISCHARGED("alta");

    /** Name shown to the clinical staff in error messages. */
    private final String description;

    public boolean canMoveTo(PatientStatus next) {
        return switch (this) {
            case REGISTERED -> next == IN_CONSULTATION || next == DISCHARGED;
            case IN_CONSULTATION -> next == DISCHARGED;
            case DISCHARGED -> false;
        };
    }

    /** Active patients are the ones still in the emergency room (not discharged). */
    public boolean isActive() {
        return this != DISCHARGED;
    }
}
