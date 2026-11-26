package com.vidacruz.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the operation report (RF-05).
 *
 * @param maxRange longest range a report may cover
 */
@ConfigurationProperties(prefix = "report")
public record ReportProperties(Duration maxRange) {
}
