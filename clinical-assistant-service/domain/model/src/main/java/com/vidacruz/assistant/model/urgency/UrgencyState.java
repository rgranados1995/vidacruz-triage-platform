package com.vidacruz.assistant.model.urgency;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Active patients of the emergency room per triage level (RF-04 part B), every level included.
 *
 * @param activeByLevel active patients per level
 * @param updatedAt     when the latest change taken into account happened in the triage; empty before any event
 */
public record UrgencyState(Map<TriageLevel, Long> activeByLevel, Optional<Instant> updatedAt) {

    public UrgencyState {
        var complete = new EnumMap<TriageLevel, Long>(TriageLevel.class);
        for (var level : TriageLevel.values()) {
            complete.put(level, activeByLevel.getOrDefault(level, 0L));
        }
        activeByLevel = Map.copyOf(complete);
    }

    public long totalActive() {
        var total = 0L;
        for (var count : activeByLevel.values()) {
            total += count;
        }
        return total;
    }
}
