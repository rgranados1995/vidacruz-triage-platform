package com.vidacruz.triage.metrics;

import com.vidacruz.triage.model.triage.TriageLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerTriageMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerTriageMetrics metrics = new MicrometerTriageMetrics(registry);

    @Test
    void shouldStartEverySeriesAtZero() {
        assertThat(registry.find(MicrometerTriageMetrics.CLASSIFIED).counters()).hasSize(TriageLevel.values().length)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(registry.find(MicrometerTriageMetrics.ESCALATED).counters()).hasSize(TriageLevel.values().length);
        assertThat(registry.find(MicrometerTriageMetrics.ACKNOWLEDGED).counters())
                .hasSize(TriageLevel.values().length);
    }

    @Test
    void shouldCountEachFactInTheSeriesOfItsLevel() {
        metrics.patientClassified(TriageLevel.CRITICAL);
        metrics.patientClassified(TriageLevel.CRITICAL);
        metrics.patientClassified(TriageLevel.NON_URGENT_2);
        metrics.patientEscalated(TriageLevel.URGENT);
        metrics.alertAcknowledged(TriageLevel.URGENT_2);

        assertThat(count(MicrometerTriageMetrics.CLASSIFIED, TriageLevel.CRITICAL)).isEqualTo(2.0);
        assertThat(count(MicrometerTriageMetrics.CLASSIFIED, TriageLevel.NON_URGENT_2)).isEqualTo(1.0);
        assertThat(count(MicrometerTriageMetrics.CLASSIFIED, TriageLevel.URGENT)).isZero();
        assertThat(count(MicrometerTriageMetrics.ESCALATED, TriageLevel.URGENT)).isEqualTo(1.0);
        assertThat(count(MicrometerTriageMetrics.ESCALATED, TriageLevel.CRITICAL)).isZero();
        assertThat(count(MicrometerTriageMetrics.ACKNOWLEDGED, TriageLevel.URGENT_2)).isEqualTo(1.0);
        assertThat(count(MicrometerTriageMetrics.ACKNOWLEDGED, TriageLevel.URGENT)).isZero();
    }

    private double count(String name, TriageLevel level) {
        return registry.get(name).tag(MicrometerTriageMetrics.LEVEL_TAG, level.name()).counter().count();
    }
}
