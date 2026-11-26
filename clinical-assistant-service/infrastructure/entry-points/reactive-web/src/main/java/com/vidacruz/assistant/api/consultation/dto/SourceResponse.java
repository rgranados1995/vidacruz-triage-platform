package com.vidacruz.assistant.api.consultation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A section of the official corpus that supports the answer.
 */
@Schema(name = "Fuente")
public record SourceResponse(
        @JsonProperty("protocolo") @Schema(example = "PROTO-001") String protocol,
        @JsonProperty("seccion") @Schema(example = "Medidas iniciales") String section) {
}
