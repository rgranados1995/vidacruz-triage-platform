package com.vidacruz.assistant.api.urgency.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Active patients of the emergency room (every one not discharged) per triage level, built from the events of the
 * triage.
 */
@Schema(name = "EstadoUrgencias")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UrgencyStateResponse(
        @JsonProperty("totalActivos") @Schema(example = "7") long totalActive,
        @JsonProperty("porNivel")
        @Schema(description = "Pacientes activos por nivel de triaje; todos los niveles, también en cero",
                example = "{\"CRITICO\": 1, \"URGENTE\": 2, \"URGENTE_2\": 0, \"NO_URGENTE\": 3, \"NO_URGENTE_2\": 1}")
        Map<String, Long> activeByLevel,
        @JsonProperty("actualizadoEn")
        @Schema(description = "Momento del último cambio del triaje incluido (el estado es eventualmente "
                + "consistente); nulo antes del primer evento", example = "2026-11-26T10:04:12.345678Z")
        Instant updatedAt) {
}
