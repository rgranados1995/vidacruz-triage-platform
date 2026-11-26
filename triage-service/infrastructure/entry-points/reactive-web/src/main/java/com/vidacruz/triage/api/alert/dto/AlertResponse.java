package com.vidacruz.triage.api.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Escalation alert for the medical team (RF-06). It carries no personal data: the patient detail is read with its id.
 */
@Builder
@Schema(name = "AlertaEscalamiento")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AlertResponse(
        @JsonProperty("id") @Schema(example = "42") long id,
        @JsonProperty("pacienteId") @Schema(example = "3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12") UUID patientId,
        @JsonProperty("nivel") @Schema(example = "CRITICO") String level,
        @JsonProperty("slaMinutos") @Schema(example = "5") int slaMinutes,
        @JsonProperty("fechaRegistro") @Schema(example = "2026-11-25T10:00:00Z") Instant registeredAt,
        @JsonProperty("fechaEscalamiento") @Schema(example = "2026-11-25T10:05:12Z") Instant escalatedAt,
        @JsonProperty("minutosEsperaAlEscalar") @Schema(description = "Minutos completos que llevaba esperando",
                example = "5") long minutesWaitedAtEscalation,
        @JsonProperty("estado") @Schema(description = "PENDIENTE, RECONOCIDA o RESUELTA (el paciente entró a "
                + "consulta o salió de alta)", example = "PENDIENTE") String status,
        @JsonProperty("reconocidaPor") @Schema(description = "Quién tomó la alerta; nulo mientras está pendiente",
                example = "Dra. Rojas") String acknowledgedBy,
        @JsonProperty("fechaReconocimiento") @Schema(example = "2026-11-25T10:06:00Z") Instant acknowledgedAt,
        @JsonProperty("fechaResolucion") @Schema(example = "2026-11-25T10:09:30Z") Instant resolvedAt,
        @JsonProperty("actualizadaEn")
        @Schema(description = "Último cambio: si llega dos veces la misma alerta, vale la más reciente",
                example = "2026-11-25T10:06:00Z") Instant updatedAt) {
}
