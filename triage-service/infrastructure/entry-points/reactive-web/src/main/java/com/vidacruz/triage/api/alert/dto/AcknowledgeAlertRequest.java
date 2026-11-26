package com.vidacruz.triage.api.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A member of the medical team takes an alert.
 */
@Schema(name = "ReconocimientoAlerta")
public record AcknowledgeAlertRequest(
        @JsonProperty("responsable")
        @Schema(description = "Nombre o código de quien toma la alerta (3 a 100 caracteres)", example = "Dra. Rojas",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String responsible) {
}
