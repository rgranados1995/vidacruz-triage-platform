package com.vidacruz.triage.api.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Errors produced by the HTTP layer itself; the business errors (TRG-001 to TRG-003 and TRG-007 to TRG-009) come
 * from the domain.
 */
@Getter
@RequiredArgsConstructor
public enum ApiErrorCode {

    INVALID_REQUEST("TRG-004", "La solicitud no es válida"),
    UNAUTHORIZED("TRG-005", "Falta la API key o no es válida"),
    NOT_SUPPORTED("TRG-006", "La ruta o el método no están soportados"),
    UNEXPECTED("TRG-999", "Ocurrió un error inesperado; intente de nuevo más tarde");

    private final String code;
    private final String title;
}
