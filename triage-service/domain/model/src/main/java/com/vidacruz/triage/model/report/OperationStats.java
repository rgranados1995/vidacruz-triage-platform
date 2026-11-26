package com.vidacruz.triage.model.report;

import com.vidacruz.triage.model.triage.TriageLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Figures of RF-05 for a group of patients (one hour, or the whole range). The wait is the time from the
 * registration to the first entry into consultation, so it only covers the patients who entered it.
 *
 * @param registered         patients registered
 * @param registeredByLevel  patients registered per triage level (every level, zero included)
 * @param attended           registered patients who entered the consultation
 * @param averageWaitMinutes average wait of the attended patients; null when nobody was attended
 * @param p95WaitMinutes     95th percentile of that wait; null when nobody was attended
 */
@Builder
public record OperationStats(long registered, Map<TriageLevel, Long> registeredByLevel, long attended,
                             BigDecimal averageWaitMinutes, BigDecimal p95WaitMinutes) {

    public OperationStats {
        registeredByLevel = Map.copyOf(registeredByLevel);
    }
}
