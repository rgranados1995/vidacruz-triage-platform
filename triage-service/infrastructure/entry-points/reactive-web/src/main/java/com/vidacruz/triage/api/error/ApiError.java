package com.vidacruz.triage.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Error body in the RFC 9457 format ({@code application/problem+json}) with two extensions: the stable error code and
 * the invalid fields.
 */
@Schema(name = "Error", description = "RFC 9457 Problem Details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        @JsonProperty("type") @Schema(example = "about:blank") String type,
        @JsonProperty("title") @Schema(example = "Los datos del paciente no son válidos") String title,
        @JsonProperty("status") @Schema(example = "400") int status,
        @JsonProperty("detail") @Schema(example = "Revise los campos indicados en errores") String detail,
        @JsonProperty("instance") @Schema(example = "/triage/pacientes") String instance,
        @JsonProperty("codigo") @Schema(example = "TRG-001") String code,
        @JsonProperty("errores") List<ApiFieldError> errors) {

    static final String DEFAULT_TYPE = "about:blank";
}
