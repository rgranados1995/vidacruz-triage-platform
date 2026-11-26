package com.vidacruz.triage.model.report.gateways;

import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.ReportPeriod;
import reactor.core.publisher.Mono;

/**
 * Computes the operation report where the data lives: the aggregates are never calculated by iterating the patients
 * in memory (RF-05).
 */
public interface OperationReportRepository {

    Mono<OperationReport> operationReport(ReportPeriod period);
}
