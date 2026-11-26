package com.vidacruz.triage.model.report;

import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationReportTest {

    private static final Instant HOUR = Instant.parse("2026-11-25T08:00:00Z");

    @Test
    void shouldKeepItsOwnCopyOfTheHoursAndLevels() {
        var levels = new EnumMap<TriageLevel, Long>(TriageLevel.class);
        levels.put(TriageLevel.CRITICAL, 1L);
        var stats = OperationStats.builder().registered(1).registeredByLevel(levels).attended(0).build();
        var hours = new ArrayList<>(List.of(new HourlyOperation(HOUR, stats)));

        var report = new OperationReport(new ReportPeriod(HOUR, HOUR.plusSeconds(3600)), hours, stats);
        levels.put(TriageLevel.URGENT, 2L);
        hours.clear();

        assertThat(report.hours()).hasSize(1);
        assertThat(report.summary().registeredByLevel()).containsOnlyKeys(TriageLevel.CRITICAL);
    }
}
