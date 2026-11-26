package com.vidacruz.assistant.model.exception;

import lombok.Getter;

/**
 * A rule of the assistant was not met, or the assistant cannot answer right now. The entry point turns it into the
 * matching HTTP error.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final BusinessError error;
    /** Specific explanation for the staff (in Spanish), or empty when the error message is enough. */
    private final String detail;

    public BusinessException(BusinessError error, String detail) {
        super(detail.isEmpty() ? error.toString() : error + ": " + detail);
        this.error = error;
        this.detail = detail;
    }

    public BusinessException(BusinessError error, String detail, Throwable cause) {
        this(error, detail);
        initCause(cause);
    }
}
