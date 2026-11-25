package com.vidacruz.triage.model.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Business errors of the triage domain. The code is stable and part of the API contract; the message is the text
 * shown to the clinical staff, so it is written in Spanish.
 */
@Getter
@RequiredArgsConstructor
public enum BusinessError {

    INVALID_PATIENT_DATA("TRG-001", "Los datos del paciente no son válidos"),
    PATIENT_NOT_FOUND("TRG-002", "No existe un paciente con el id indicado"),
    INVALID_STATUS_TRANSITION("TRG-003", "Transición de estado no permitida");

    private final String code;
    private final String message;

    @Override
    public String toString() {
        return code + " " + message;
    }
}
