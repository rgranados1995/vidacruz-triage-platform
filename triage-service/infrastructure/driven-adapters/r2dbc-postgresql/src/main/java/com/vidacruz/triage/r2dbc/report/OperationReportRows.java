package com.vidacruz.triage.r2dbc.report;

import com.vidacruz.triage.model.report.HourlyOperation;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.OperationStats;
import com.vidacruz.triage.model.report.ReportPeriod;
import com.vidacruz.triage.model.triage.TriageLevel;
import io.r2dbc.spi.Readable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Maps the rows of {@link OperationReportSql#HOURLY_WITH_SUMMARY} to the report. No figure is calculated here: the
 * rows already carry them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OperationReportRows {

    /** A result row: one hour, or the whole range. */
    record ReportRow(Instant hour, boolean wholeRange, OperationStats stats) {
    }

    static ReportRow toRow(Readable row) {
        return new ReportRow(row.get("hour", Instant.class), Boolean.TRUE.equals(row.get("whole_range", Boolean.class)),
                OperationStats.builder()
                        .registered(row.get("registered", Long.class))
                        .registeredByLevel(byLevel(row))
                        .attended(row.get("attended", Long.class))
                        .averageWaitMinutes(row.get("average_wait_minutes", BigDecimal.class))
                        .p95WaitMinutes(row.get("p95_wait_minutes", BigDecimal.class))
                        .build());
    }

    /** The query always returns exactly one whole-range row, after the hours. */
    static OperationReport toReport(ReportPeriod period, List<ReportRow> rows) {
        var hours = new ArrayList<HourlyOperation>();
        OperationStats summary = null;
        for (var row : rows) {
            if (row.wholeRange()) {
                summary = row.stats();
            } else {
                hours.add(new HourlyOperation(row.hour(), row.stats()));
            }
        }
        return new OperationReport(period, hours, summary);
    }

    /** Each level has its own column, named after the level ({@code critical}, {@code urgent_2}...). */
    private static EnumMap<TriageLevel, Long> byLevel(Readable row) {
        var levels = new EnumMap<TriageLevel, Long>(TriageLevel.class);
        for (var level : TriageLevel.values()) {
            levels.put(level, row.get(level.name().toLowerCase(Locale.ROOT), Long.class));
        }
        return levels;
    }
}
