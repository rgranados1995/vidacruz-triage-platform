package com.vidacruz.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SLA monitor settings that belong to the domain policy (the interval belongs to the scheduler).
 *
 * @param batchSize maximum patients escalated per round
 */
@ConfigurationProperties(prefix = "sla-monitor")
public record SlaMonitorProperties(int batchSize) {
}
