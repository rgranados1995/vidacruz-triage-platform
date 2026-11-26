package com.vidacruz.assistant.api.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Errors produced by the HTTP layer itself; the business errors (AST-001 to AST-003) come from the domain.
 */
@Getter
@RequiredArgsConstructor
public enum ApiErrorCode {

    INVALID_REQUEST("AST-004", "La solicitud no es válida"),
    UNAUTHORIZED("AST-005", "Falta la API key o no es válida"),
    NOT_SUPPORTED("AST-006", "La ruta o el método no están soportados"),
    UNEXPECTED("AST-999", "Ocurrió un error inesperado; intente de nuevo más tarde");

    private final String code;
    private final String title;
}
