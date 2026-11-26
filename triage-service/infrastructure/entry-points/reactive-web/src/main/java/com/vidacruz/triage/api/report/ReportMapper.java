package com.vidacruz.triage.api.report;

import com.vidacruz.triage.api.report.dto.HourlyOperationResponse;
import com.vidacruz.triage.api.report.dto.OperationReportResponse;
import com.vidacruz.triage.api.report.dto.OperationSummaryResponse;
import com.vidacruz.triage.contract.ContractCodes;
import com.vidacruz.triage.model.report.HourlyOperation;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.OperationStats;
import com.vidacruz.triage.model.triage.TriageLevel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates the report to the HTTP contract: levels with their contract codes, always in the same order.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReportMapper {

    static OperationReportResponse toResponse(OperationReport report) {
        var hours = new ArrayList<HourlyOperationResponse>();
        for (HourlyOperation hour : report.hours()) {
            hours.add(toHourly(hour));
        }
        var summary = report.summary();
        return new OperationReportResponse(report.period().from(), report.period().to(), hours,
                new OperationSummaryResponse(summary.registered(), byLevel(summary), summary.attended(),
                        summary.averageWaitMinutes(), summary.p95WaitMinutes()));
    }

    private static HourlyOperationResponse toHourly(HourlyOperation hour) {
        var stats = hour.stats();
        return new HourlyOperationResponse(hour.hour(), stats.registered(), byLevel(stats), stats.attended(),
                stats.averageWaitMinutes(), stats.p95WaitMinutes());
    }

    private static Map<String, Long> byLevel(OperationStats stats) {
        var levels = new LinkedHashMap<String, Long>();
        for (var level : TriageLevel.values()) {
            levels.put(ContractCodes.levelCode(level), stats.registeredByLevel().getOrDefault(level, 0L));
        }
        return levels;
    }
}
