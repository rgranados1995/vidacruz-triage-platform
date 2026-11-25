package com.vidacruz.triage.api.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Body of RF-01. Every field is nullable here: a missing value is reported by the domain validation with the rest of
 * the invalid fields.
 */
@Schema(name = "RegistroPaciente", description = "Datos tomados en la recepción de urgencias")
public record RegisterPatientRequest(
        @JsonProperty("nombreCompleto") @Schema(example = "Ana María Pérez", minLength = 3, maxLength = 100)
        String fullName,
        @JsonProperty("edad") @Schema(example = "54", minimum = "0", maximum = "120")
        Integer age,
        @JsonProperty("frecuenciaCardiaca")
        @Schema(description = "lpm", example = "130", minimum = "20", maximum = "250")
        Integer heartRate,
        @JsonProperty("presionSistolica")
        @Schema(description = "mmHg", example = "180", minimum = "60", maximum = "260")
        Integer systolicPressure,
        @JsonProperty("presionDiastolica")
        @Schema(description = "mmHg, menor que la sistólica", example = "110", minimum = "30", maximum = "160")
        Integer diastolicPressure,
        @JsonProperty("spo2") @Schema(description = "%", example = "97", minimum = "70", maximum = "100")
        Integer oxygenSaturation,
        @JsonProperty("temperatura") @Schema(description = "°C", example = "36.8", minimum = "34.0", maximum = "42.0")
        BigDecimal temperature,
        @JsonProperty("dolor") @Schema(description = "0 a 10", example = "8", minimum = "0", maximum = "10")
        Integer painLevel,
        @JsonProperty("gcs") @Schema(description = "Escala de Glasgow", example = "15", minimum = "3", maximum = "15")
        Integer glasgowComaScale,
        @JsonProperty("quejaPrincipal") @Schema(example = "Dolor torácico", maxLength = 500)
        String chiefComplaint) {
}
