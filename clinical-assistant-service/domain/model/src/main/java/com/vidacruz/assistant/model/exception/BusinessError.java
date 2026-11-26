package com.vidacruz.assistant.model.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Business errors of the clinical assistant. The code is stable and part of the API contract; the message is the
 * text shown to the medical staff, so it is written in Spanish.
 */
@Getter
@RequiredArgsConstructor
public enum BusinessError {

    INVALID_QUESTION("AST-001", "La pregunta no es válida"),
    ASSISTANT_UNAVAILABLE("AST-002", "El asistente clínico no está disponible en este momento");

    private final String code;
    private final String message;

    @Override
    public String toString() {
        return code + " " + message;
    }
}
