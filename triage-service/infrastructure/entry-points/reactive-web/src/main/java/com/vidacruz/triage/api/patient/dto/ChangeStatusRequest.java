package com.vidacruz.triage.api.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of RF-02.
 */
@Schema(name = "CambioEstado")
public record ChangeStatusRequest(
        @JsonProperty("estado")
        @Schema(example = "EN_CONSULTA", allowableValues = {"REGISTRADO", "EN_CONSULTA", "ALTA"})
        String status) {
}
