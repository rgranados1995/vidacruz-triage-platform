package com.vidacruz.triage.model.triage;

import com.vidacruz.triage.model.patient.VitalSigns;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

/**
 * Objective classification of RN-01. The levels are evaluated from the most severe down and the first one whose
 * conditions match wins, so when conditions of several levels match the most severe one applies. Expects vital
 * signs already validated (no nulls).
 */
@RequiredArgsConstructor
public class TriageClassifier {

    private static final int HEART_RATE_CRITICAL_LOW = 40;
    private static final int HEART_RATE_CRITICAL_HIGH = 120;
    private static final int HEART_RATE_URGENT_2_LOW = 90;
    private static final int OXYGEN_CRITICAL_BELOW = 90;
    private static final int OXYGEN_URGENT_HIGH = 93;
    private static final int SYSTOLIC_CRITICAL_BELOW = 90;
    private static final int SYSTOLIC_URGENT_HIGH = 100;
    private static final int GLASGOW_CRITICAL_HIGH = 8;
    private static final int GLASGOW_URGENT_HIGH = 13;
    private static final int PAIN_URGENT_2_LOW = 7;
    private static final BigDecimal TEMPERATURE_URGENT_LOW = new BigDecimal("38.5");
    private static final BigDecimal TEMPERATURE_URGENT_2_LOW = new BigDecimal("38.0");

    private static final List<LevelRule> RULES = List.of(
            new LevelRule(TriageLevel.CRITICAL, TriageClassifier::isCritical),
            new LevelRule(TriageLevel.URGENT, TriageClassifier::isUrgent),
            new LevelRule(TriageLevel.URGENT_2, TriageClassifier::isUrgent2));

    private final AdministrativeComplaints administrativeComplaints;

    public TriageLevel classify(VitalSigns vitalSigns, String chiefComplaint) {
        for (var rule : RULES) {
            if (rule.condition().test(vitalSigns)) {
                return rule.level();
            }
        }
        return administrativeComplaints.contains(chiefComplaint) ? TriageLevel.NON_URGENT_2 : TriageLevel.NON_URGENT;
    }

    private static boolean isCritical(VitalSigns signs) {
        return signs.heartRate() < HEART_RATE_CRITICAL_LOW
                || signs.heartRate() > HEART_RATE_CRITICAL_HIGH
                || signs.oxygenSaturation() < OXYGEN_CRITICAL_BELOW
                || signs.systolicPressure() < SYSTOLIC_CRITICAL_BELOW
                || signs.glasgowComaScale() <= GLASGOW_CRITICAL_HIGH;
    }

    private static boolean isUrgent(VitalSigns signs) {
        return between(signs.oxygenSaturation(), OXYGEN_CRITICAL_BELOW, OXYGEN_URGENT_HIGH)
                || between(signs.systolicPressure(), SYSTOLIC_CRITICAL_BELOW, SYSTOLIC_URGENT_HIGH)
                || signs.temperature().compareTo(TEMPERATURE_URGENT_LOW) >= 0
                || between(signs.glasgowComaScale(), GLASGOW_CRITICAL_HIGH + 1, GLASGOW_URGENT_HIGH);
    }

    private static boolean isUrgent2(VitalSigns signs) {
        return between(signs.heartRate(), HEART_RATE_URGENT_2_LOW, HEART_RATE_CRITICAL_HIGH)
                || (signs.temperature().compareTo(TEMPERATURE_URGENT_2_LOW) >= 0
                && signs.temperature().compareTo(TEMPERATURE_URGENT_LOW) < 0)
                || signs.painLevel() >= PAIN_URGENT_2_LOW;
    }

    private static boolean between(int value, int lowInclusive, int highInclusive) {
        return value >= lowInclusive && value <= highInclusive;
    }

    private record LevelRule(TriageLevel level, Predicate<VitalSigns> condition) {
    }
}
