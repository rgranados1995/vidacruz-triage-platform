package com.vidacruz.triage.model.triage;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maximum minutes from the registration to the consultation for each level (RN-02). The minutes come from the
 * configuration so the escalation can be tested without waiting for the real SLA.
 */
public class SlaPolicy {

    private final Map<TriageLevel, Integer> minutesByLevel;

    /**
     * @throws IllegalArgumentException when a level has no SLA or a non-positive one, so a bad configuration stops
     *                                  the service at startup instead of misclassifying patients later
     */
    public SlaPolicy(Map<TriageLevel, Integer> minutesByLevel) {
        this.minutesByLevel = new EnumMap<>(TriageLevel.class);
        for (var level : TriageLevel.values()) {
            var minutes = minutesByLevel.get(level);
            if (minutes == null || minutes <= 0) {
                throw new IllegalArgumentException("The SLA of level " + level + " must be a positive number of "
                        + "minutes, but it is " + minutes);
            }
            this.minutesByLevel.put(level, minutes);
        }
    }

    public int minutesFor(TriageLevel level) {
        return minutesByLevel.get(level);
    }
}
