package com.vidacruz.triage.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param pollInterval time between two rounds of the outbox relay
 */
@ConfigurationProperties(prefix = "outbox")
public record OutboxRelayProperties(Duration pollInterval) {
}
