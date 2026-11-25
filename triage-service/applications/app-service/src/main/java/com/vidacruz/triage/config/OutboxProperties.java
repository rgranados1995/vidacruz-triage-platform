package com.vidacruz.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Outbox relay settings that belong to the domain policy (the poll interval belongs to the scheduler).
 *
 * @param batchSize maximum events published per round
 * @param lease     reservation of a claimed event before another round can retry it
 */
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(int batchSize, Duration lease) {
}
