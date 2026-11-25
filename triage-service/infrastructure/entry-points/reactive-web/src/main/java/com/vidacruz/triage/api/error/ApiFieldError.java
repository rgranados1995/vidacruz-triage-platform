package com.vidacruz.triage.api.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One invalid field of the request, named as in the contract.
 */
@Schema(name = "ErrorCampo")
public record ApiFieldError(
        @JsonProperty("campo") @Schema(example = "frecuenciaCardiaca") String field,
        @JsonProperty("mensaje") @Schema(example = "La frecuencia cardíaca debe estar entre 20 y 250 lpm")
        String message) {
}
