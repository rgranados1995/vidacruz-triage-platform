package com.vidacruz.triage.api.report;

import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.usecase.getoperationreport.GetOperationReportUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * HTTP side of RF-05: reads the range from the query, asks for the report and writes it. Errors are signals handled
 * by the global error handler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportHandler {

    static final String FROM = "desde";
    static final String TO = "hasta";

    private final GetOperationReportUseCase getOperationReport;

    public Mono<ServerResponse> operationReport(ServerRequest request) {
        return instant(request, FROM)
                .zipWith(instant(request, TO))
                .flatMap(range -> getOperationReport.getOperationReport(range.getT1(), range.getT2()))
                .doOnNext(ReportHandler::logReport)
                .flatMap(report -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ReportMapper.toResponse(report)));
    }

    /** A date and time in ISO 8601 with its offset: without it the instant would be ambiguous. */
    private static Mono<Instant> instant(ServerRequest request, String name) {
        return Mono.justOrEmpty(request.queryParam(name))
                .switchIfEmpty(Mono.defer(() -> Mono.error(
                        new InvalidRequestException(name, "El parámetro " + name + " es obligatorio"))))
                .map(value -> OffsetDateTime.parse(value).toInstant())
                .onErrorMap(DateTimeParseException.class, error -> new InvalidRequestException(name,
                        "El parámetro " + name + " debe ser una fecha y hora ISO 8601 con zona, por ejemplo "
                                + "2026-11-25T08:00:00-05:00 (en la URL, el signo + se escribe %2B)"));
    }

    private static void logReport(OperationReport report) {
        log.atInfo()
                .addKeyValue("from", report.period().from())
                .addKeyValue("to", report.period().to())
                .addKeyValue("registered", report.summary().registered())
                .log("Operation report generated");
    }
}
