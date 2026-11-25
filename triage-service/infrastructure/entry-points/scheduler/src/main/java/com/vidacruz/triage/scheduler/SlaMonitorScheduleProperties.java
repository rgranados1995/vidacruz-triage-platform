package com.vidacruz.triage.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param interval time between two rounds of the SLA monitor (RF-03 requires it configurable)
 */
@ConfigurationProperties(prefix = "sla-monitor")
public record SlaMonitorScheduleProperties(Duration interval) {
}
