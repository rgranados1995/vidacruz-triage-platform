package com.vidacruz.assistant.api.error;

import lombok.Getter;

/**
 * The request cannot be read: missing or malformed body, a value of the wrong type or outside the allowed codes.
 * Answered with 400 and the name of the field in the contract.
 */
@Getter
public class InvalidRequestException extends RuntimeException {

    /** Field of the contract (JSON name or path variable), or null when the whole body is invalid. */
    private final String field;
    private final String reason;

    public InvalidRequestException(String field, String reason) {
        super("Invalid request field " + field + ": " + reason);
        this.field = field;
        this.reason = reason;
    }
}
