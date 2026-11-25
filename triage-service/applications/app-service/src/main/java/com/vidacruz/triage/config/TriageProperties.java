package com.vidacruz.triage.config;

import com.vidacruz.triage.model.triage.TriageLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Clinical parameters that RF-03 requires to be configurable.
 *
 * @param slaMinutes                SLA of RN-02 per level, in minutes
 * @param administrativeComplaints chief complaints that lower a non-urgent patient to level 5 (RN-01)
 */
@ConfigurationProperties(prefix = "triage")
public record TriageProperties(Map<TriageLevel, Integer> slaMinutes, List<String> administrativeComplaints) {

    public TriageProperties {
        Objects.requireNonNull(slaMinutes, "triage.sla-minutes is required");
        Objects.requireNonNull(administrativeComplaints, "triage.administrative-complaints is required");
    }
}
