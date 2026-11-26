package com.vidacruz.triage.api.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the alert stream.
 *
 * @param heartbeat pause between heartbeats of an idle stream, shorter than the idle timeout of any proxy in between
 */
@ConfigurationProperties(prefix = "alerts.stream")
public record AlertStreamProperties(Duration heartbeat) {

    public AlertStreamProperties {
        if (heartbeat == null || heartbeat.isNegative() || heartbeat.isZero()) {
            throw new IllegalArgumentException("The alert stream heartbeat must be positive, but it is " + heartbeat);
        }
    }
}
