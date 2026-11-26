package com.vidacruz.triage.r2dbc.report;

import com.vidacruz.triage.model.report.HourlyOperation;
import com.vidacruz.triage.model.report.OperationStats;
import com.vidacruz.triage.model.report.ReportPeriod;
import com.vidacruz.triage.model.triage.TriageLevel;
import io.r2dbc.spi.Readable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mapping of the result rows without a database (the query itself is covered by the integration test).
 */
class OperationReportRowsTest {

    private static final Instant HOUR = Instant.parse("2026-11-25T13:00:00Z");

    @Test
    void shouldReadEveryFigureOfARow() {
        var row = mock(Readable.class);
        when(row.get("hour", Instant.class)).thenReturn(HOUR);
        when(row.get("whole_range", Boolean.class)).thenReturn(Boolean.FALSE);
        when(row.get("registered", Long.class)).thenReturn(6L);
        when(row.get("critical", Long.class)).thenReturn(1L);
        when(row.get("urgent", Long.class)).thenReturn(2L);
        when(row.get("urgent_2", Long.class)).thenReturn(0L);
        when(row.get("non_urgent", Long.class)).thenReturn(3L);
        when(row.get("non_urgent_2", Long.class)).thenReturn(0L);
        when(row.get("attended", Long.class)).thenReturn(4L);
        when(row.get("average_wait_minutes", BigDecimal.class)).thenReturn(new BigDecimal("12.50"));
        when(row.get("p95_wait_minutes", BigDecimal.class)).thenReturn(new BigDecimal("30.25"));

        var read = OperationReportRows.toRow(row);

        assertThat(read.hour()).isEqualTo(HOUR);
        assertThat(read.wholeRange()).isFalse();
        assertThat(read.stats()).isEqualTo(OperationStats.builder()
                .registered(6)
                .registeredByLevel(Map.of(TriageLevel.CRITICAL, 1L, TriageLevel.URGENT, 2L, TriageLevel.URGENT_2, 0L,
                        TriageLevel.NON_URGENT, 3L, TriageLevel.NON_URGENT_2, 0L))
                .attended(4)
                .averageWaitMinutes(new BigDecimal("12.50"))
                .p95WaitMinutes(new BigDecimal("30.25"))
                .build());
    }

    @Test
    void shouldSeparateTheHoursFromTheWholeRange() {
        var first = stats(2);
        var second = stats(0);
        var whole = stats(2);
        var period = new ReportPeriod(HOUR, HOUR.plusSeconds(7200));

        var report = OperationReportRows.toReport(period, List.of(
                new OperationReportRows.ReportRow(HOUR, false, first),
                new OperationReportRows.ReportRow(HOUR.plusSeconds(3600), false, second),
                new OperationReportRows.ReportRow(null, true, whole)));

        assertThat(report.period()).isEqualTo(period);
        assertThat(report.hours()).extracting(HourlyOperation::hour).containsExactly(HOUR, HOUR.plusSeconds(3600));
        assertThat(report.hours()).extracting(HourlyOperation::stats).containsExactly(first, second);
        assertThat(report.summary()).isSameAs(whole);
    }

    private static OperationStats stats(long registered) {
        return OperationStats.builder().registered(registered).registeredByLevel(Map.of()).build();
    }
}
