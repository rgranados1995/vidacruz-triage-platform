package com.vidacruz.triage.api.report;

import com.vidacruz.triage.api.error.ApiError;
import com.vidacruz.triage.api.report.dto.OperationReportResponse;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springdoc.core.fn.builders.operation.Builder;

import java.time.OffsetDateTime;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

/**
 * OpenAPI documentation of the report routes, written with the springdoc DSL next to the routes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReportApiDocs {

    private static final String DATE_FORMAT = " Fecha y hora ISO 8601 con zona; en la URL el + se envía como %2B.";

    static void operationReport(Builder operation) {
        operation.operationId("reporteOperacion").tag("Reportes")
                .summary("Reporte de operación por hora (RF-05)")
                .description("Por cada hora del rango, también las que no tuvieron pacientes: registrados, conteo "
                        + "por nivel, atendidos, espera promedio y p95 (del registro a la primera entrada a "
                        + "consulta, en minutos). Cada paciente cuenta en la hora en que se registró. Las horas van "
                        + "en UTC; con zonas de hora entera coinciden con las horas locales. Incluye el resumen de "
                        + "todo el rango.")
                .parameter(parameterBuilder().in(ParameterIn.QUERY).name(ReportHandler.FROM).required(true)
                        .description("Inicio del rango (incluido)." + DATE_FORMAT).example("2026-11-25T08:00:00-05:00")
                        .implementation(OffsetDateTime.class))
                .parameter(parameterBuilder().in(ParameterIn.QUERY).name(ReportHandler.TO).required(true)
                        .description("Fin del rango (excluido)." + DATE_FORMAT).example("2026-11-25T12:00:00-05:00")
                        .implementation(OffsetDateTime.class))
                .response(responseBuilder().responseCode("200").description("Reporte del rango")
                        .implementation(OperationReportResponse.class))
                .response(error("400", "Falta un parámetro, no es una fecha ISO 8601 con zona (TRG-004), o desde no "
                        + "es anterior a hasta o el rango supera el máximo (TRG-007)"))
                .response(error("401", "Falta la API key o no es válida"))
                .response(error("500", "Error inesperado"));
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder error(String status, String description) {
        return responseBuilder().responseCode(status).description(description).implementation(ApiError.class);
    }
}
