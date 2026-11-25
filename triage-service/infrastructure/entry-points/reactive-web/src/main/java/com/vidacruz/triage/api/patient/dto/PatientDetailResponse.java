package com.vidacruz.triage.api.patient.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Full patient of RF-02: data, current status, flags and time in the current status.
 */
@Builder
@Schema(name = "DetallePaciente")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PatientDetailResponse(
        @JsonProperty("id") @Schema(example = "3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12") UUID id,
        @JsonProperty("nombreCompleto") @Schema(example = "Ana María Pérez") String fullName,
        @JsonProperty("edad") @Schema(example = "54") Integer age,
        @JsonProperty("frecuenciaCardiaca") @Schema(example = "130") Integer heartRate,
        @JsonProperty("presionSistolica") @Schema(example = "180") Integer systolicPressure,
        @JsonProperty("presionDiastolica") @Schema(example = "110") Integer diastolicPressure,
        @JsonProperty("spo2") @Schema(example = "97") Integer oxygenSaturation,
        @JsonProperty("temperatura") @Schema(example = "36.8") BigDecimal temperature,
        @JsonProperty("dolor") @Schema(example = "8") Integer painLevel,
        @JsonProperty("gcs") @Schema(example = "15") Integer glasgowComaScale,
        @JsonProperty("quejaPrincipal") @Schema(example = "Dolor torácico") String chiefComplaint,
        @JsonProperty("nivel") @Schema(example = "CRITICO") String level,
        @JsonProperty("slaMinutos") @Schema(example = "5") Integer slaMinutes,
        @JsonProperty("estado") @Schema(example = "EN_CONSULTA") String status,
        @JsonProperty("escalado") @Schema(description = "Superó su SLA sin entrar a consulta", example = "false")
        boolean escalated,
        @JsonProperty("fechaRegistro") @Schema(example = "2026-11-25T10:00:00Z") Instant registeredAt,
        @JsonProperty("fechaCambioEstado") @Schema(example = "2026-11-25T10:04:00Z") Instant statusChangedAt,
        @JsonProperty("fechaInicioConsulta")
        @Schema(description = "Nulo mientras no haya entrado a consulta", example = "2026-11-25T10:04:00Z")
        Instant consultationStartedAt,
        @JsonProperty("tiempoEnEstadoActual") @Schema(description = "Duración ISO 8601", example = "PT12M30S")
        String timeInCurrentStatus) {
}
