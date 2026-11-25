package com.vidacruz.triage.model.exception;

import lombok.Getter;

/**
 * A rule of the triage domain was not met. The entry point turns it into a client error; it is never a failure of
 * the service itself.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final BusinessError error;
    /** Specific explanation for the staff (in Spanish), or empty when the error message is enough. */
    private final String detail;

    public BusinessException(BusinessError error) {
        this(error, "");
    }

    public BusinessException(BusinessError error, String detail) {
        super(detail.isEmpty() ? error.toString() : error + ": " + detail);
        this.error = error;
        this.detail = detail;
    }
}
