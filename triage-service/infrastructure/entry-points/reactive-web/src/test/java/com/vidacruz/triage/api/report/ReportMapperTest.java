package com.vidacruz.triage.api.report;

import com.vidacruz.triage.model.report.HourlyOperation;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.OperationStats;
import com.vidacruz.triage.model.report.ReportPeriod;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMapperTest {

    private static final Instant FROM = Instant.parse("2026-11-25T13:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-25T14:00:00Z");

    @Test
    void shouldListEveryLevelWithItsContractCodeInOrder() {
        var hourly = OperationStats.builder()
                .registered(3)
                .registeredByLevel(Map.of(TriageLevel.URGENT_2, 3L))
                .attended(1)
                .averageWaitMinutes(new BigDecimal("8.00"))
                .p95WaitMinutes(new BigDecimal("8.00"))
                .build();
        var summary = OperationStats.builder()
                .registered(5)
                .registeredByLevel(Map.of(TriageLevel.URGENT_2, 3L, TriageLevel.CRITICAL, 2L))
                .attended(2)
                .averageWaitMinutes(new BigDecimal("6.50"))
                .p95WaitMinutes(new BigDecimal("7.85"))
                .build();
        var report = new OperationReport(new ReportPeriod(FROM, TO), List.of(new HourlyOperation(FROM, hourly)),
                summary);

        var response = ReportMapper.toResponse(report);

        assertThat(response.from()).isEqualTo(FROM);
        assertThat(response.to()).isEqualTo(TO);
        var hour = response.hours().getFirst();
        assertThat(hour.hour()).isEqualTo(FROM);
        assertThat(hour.registered()).isEqualTo(3);
        assertThat(hour.attended()).isEqualTo(1);
        assertThat(hour.averageWaitMinutes()).isEqualByComparingTo("8");
        assertThat(hour.p95WaitMinutes()).isEqualByComparingTo("8");
        assertThat(hour.registeredByLevel()).containsExactly(Map.entry("CRITICO", 0L), Map.entry("URGENTE", 0L),
                Map.entry("URGENTE_2", 3L), Map.entry("NO_URGENTE", 0L), Map.entry("NO_URGENTE_2", 0L));
        var whole = response.summary();
        assertThat(whole.registered()).isEqualTo(5);
        assertThat(whole.attended()).isEqualTo(2);
        assertThat(whole.averageWaitMinutes()).isEqualByComparingTo("6.5");
        assertThat(whole.p95WaitMinutes()).isEqualByComparingTo("7.85");
        assertThat(whole.registeredByLevel()).containsEntry("CRITICO", 2L).containsEntry("URGENTE_2", 3L);
    }
}
