package com.vidacruz.triage.r2dbc.report;

import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.ReportPeriod;
import com.vidacruz.triage.model.report.gateways.OperationReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Operation report (RF-05) computed by PostgreSQL in a single query. The application receives one row per hour of
 * the range plus the whole-range row (at most a few hundred, bounded by the maximum range), never the patients.
 */
@Repository
@RequiredArgsConstructor
public class PostgresOperationReportRepository implements OperationReportRepository {

    private final DatabaseClient client;

    @Override
    public Mono<OperationReport> operationReport(ReportPeriod period) {
        return client.sql(OperationReportSql.HOURLY_WITH_SUMMARY)
                .bind("from", period.from())
                .bind("to", period.to())
                .map(OperationReportRows::toRow)
                .all()
                .collectList()
                .map(rows -> OperationReportRows.toReport(period, rows));
    }
}
