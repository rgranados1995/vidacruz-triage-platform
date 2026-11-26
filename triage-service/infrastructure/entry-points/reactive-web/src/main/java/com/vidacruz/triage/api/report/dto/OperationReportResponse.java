package com.vidacruz.triage.api.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Operation report of RF-05: every hour of the range, hours without patients included, and the whole range.
 */
@Schema(name = "ReporteOperacion")
public record OperationReportResponse(
        @JsonProperty("desde") @Schema(description = "Inicio del rango (incluido), en UTC",
                example = "2026-11-25T13:00:00Z") Instant from,
        @JsonProperty("hasta") @Schema(description = "Fin del rango (excluido), en UTC",
                example = "2026-11-25T17:00:00Z") Instant to,
        @JsonProperty("horas") List<HourlyOperationResponse> hours,
        @JsonProperty("resumen") @Schema(description = "Todo el rango, calculado sobre los pacientes (el p95 del "
                + "rango no se puede obtener de los p95 por hora)") OperationSummaryResponse summary) {
}
