package com.vidacruz.triage.api.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of RF-01.
 */
@Schema(name = "PacienteRegistrado")
public record RegisteredPatientResponse(
        @JsonProperty("id") @Schema(example = "3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12")
        UUID id,
        @JsonProperty("nivel")
        @Schema(example = "CRITICO",
                allowableValues = {"CRITICO", "URGENTE", "URGENTE_2", "NO_URGENTE", "NO_URGENTE_2"})
        String level,
        @JsonProperty("slaMinutos") @Schema(example = "5")
        Integer slaMinutes,
        @JsonProperty("estado") @Schema(example = "REGISTRADO")
        String status,
        @JsonProperty("fechaRegistro") @Schema(description = "ISO 8601, UTC", example = "2026-11-25T10:00:00Z")
        Instant registeredAt) {
}
