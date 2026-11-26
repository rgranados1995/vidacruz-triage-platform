package com.vidacruz.triage.api.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Figures of the whole range of the report.
 */
@Schema(name = "ResumenOperacion")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OperationSummaryResponse(
        @JsonProperty("totalRegistrados") @Schema(example = "7") long registered,
        @JsonProperty("porNivel")
        @Schema(description = "Pacientes registrados por nivel de triaje; todos los niveles, también en cero",
                example = "{\"CRITICO\": 1, \"URGENTE\": 2, \"URGENTE_2\": 0, \"NO_URGENTE\": 3, \"NO_URGENTE_2\": 1}")
        Map<String, Long> registeredByLevel,
        @JsonProperty("atendidos") @Schema(description = "Registrados que ya entraron a consulta", example = "5")
        long attended,
        @JsonProperty("esperaPromedioMinutos")
        @Schema(description = "Promedio de minutos del registro a la primera entrada a consulta de los atendidos; "
                + "nulo si nadie fue atendido", example = "12.4")
        BigDecimal averageWaitMinutes,
        @JsonProperty("esperaP95Minutos")
        @Schema(description = "Percentil 95 de esa espera (interpolado); nulo si nadie fue atendido", example = "31.0")
        BigDecimal p95WaitMinutes) {
}
