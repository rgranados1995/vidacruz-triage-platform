package com.vidacruz.assistant.api.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One invalid field of the request, named as in the contract.
 */
@Schema(name = "ErrorCampo")
public record ApiFieldError(
        @JsonProperty("campo") @Schema(example = "pregunta") String field,
        @JsonProperty("mensaje") @Schema(example = "la pregunta debe tener entre 3 y 1000 caracteres")
        String message) {
}
