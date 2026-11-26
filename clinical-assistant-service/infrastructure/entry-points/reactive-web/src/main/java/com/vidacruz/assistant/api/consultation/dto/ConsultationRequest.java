package com.vidacruz.assistant.api.consultation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A question of a doctor about the clinical protocols.
 */
@Schema(name = "Consulta")
public record ConsultationRequest(
        @JsonProperty("pregunta")
        @Schema(description = "Pregunta en lenguaje natural (3 a 1000 caracteres)",
                example = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String question) {
}
