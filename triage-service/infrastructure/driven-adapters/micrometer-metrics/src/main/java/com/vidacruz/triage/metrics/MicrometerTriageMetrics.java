package com.vidacruz.triage.metrics;

import com.vidacruz.triage.model.metrics.gateways.TriageMetrics;
import com.vidacruz.triage.model.triage.TriageLevel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Counters of the triage in Micrometer, one series per level. Every series is registered at startup with zero, so a
 * rate over a level that had no patients yet is 0 and not "no data". Prometheus exposes them with the
 * {@code _total} suffix, for example {@code triage_patients_classified_total{level="CRITICAL"}}.
 */
@Component
public class MicrometerTriageMetrics implements TriageMetrics {

    static final String CLASSIFIED = "triage.patients.classified";
    static final String ESCALATED = "triage.patients.escalated";
    static final String ACKNOWLEDGED = "triage.alerts.acknowledged";
    static final String LEVEL_TAG = "level";

    private final Map<TriageLevel, Counter> classified;
    private final Map<TriageLevel, Counter> escalated;
    private final Map<TriageLevel, Counter> acknowledged;

    public MicrometerTriageMetrics(MeterRegistry registry) {
        classified = countersByLevel(registry, CLASSIFIED, "Patients registered and classified, by triage level");
        escalated = countersByLevel(registry, ESCALATED, "Patients escalated because their SLA passed, by level");
        acknowledged = countersByLevel(registry, ACKNOWLEDGED, "Alerts taken by the medical team, by triage level");
    }

    @Override
    public void patientClassified(TriageLevel level) {
        classified.get(level).increment();
    }

    @Override
    public void patientEscalated(TriageLevel level) {
        escalated.get(level).increment();
    }

    @Override
    public void alertAcknowledged(TriageLevel level) {
        acknowledged.get(level).increment();
    }

    private static Map<TriageLevel, Counter> countersByLevel(MeterRegistry registry, String name, String description) {
        var counters = new EnumMap<TriageLevel, Counter>(TriageLevel.class);
        for (var level : TriageLevel.values()) {
            counters.put(level, Counter.builder(name)
                    .description(description)
                    .tag(LEVEL_TAG, level.name())
                    .register(registry));
        }
        return counters;
    }
}
