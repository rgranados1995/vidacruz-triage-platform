package com.vidacruz.triage.contract.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Data of a patient event. Only what a consumer needs to follow the emergency room: no name, vital signs nor chief
 * complaint (data minimization).
 */
public record PatientEventData(
        @JsonProperty("pacienteId") UUID patientId,
        @JsonProperty("nivel") String level,
        @JsonProperty("estadoAnterior") String previousStatus,
        @JsonProperty("estado") String status,
        @JsonProperty("escalado") boolean escalated,
        @JsonProperty("slaMinutos") Integer slaMinutes,
        @JsonProperty("fechaRegistro") Instant registeredAt,
        @JsonProperty("ocurridoEn") Instant occurredAt) {
}
