package com.vidacruz.triage.usecase.getoperationreport;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.ReportPolicy;
import com.vidacruz.triage.model.report.gateways.OperationReportRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * RF-05: operation report of a range, grouped by hour. The range is checked before any query runs.
 */
@RequiredArgsConstructor
public class GetOperationReportUseCase {

    private final OperationReportRepository operationReportRepository;
    private final ReportPolicy reportPolicy;

    /**
     * Emits the report from {@code from} (included) to {@code to} (excluded), or a {@link BusinessException} with
     * {@link BusinessError#INVALID_REPORT_PERIOD} when the range is not valid.
     */
    public Mono<OperationReport> getOperationReport(Instant from, Instant to) {
        return Mono.fromCallable(() -> reportPolicy.periodBetween(from, to))
                .flatMap(operationReportRepository::operationReport);
    }
}
