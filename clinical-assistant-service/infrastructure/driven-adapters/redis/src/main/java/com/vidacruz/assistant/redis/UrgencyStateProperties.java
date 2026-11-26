package com.vidacruz.assistant.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the projection of the emergency room.
 *
 * @param dischargedRetention how long a discharged patient is remembered, so a late older event cannot bring it
 *                            back; at least the retention of the topic
 */
@ConfigurationProperties(prefix = "adapters.urgency-state")
public record UrgencyStateProperties(Duration dischargedRetention) {

    public UrgencyStateProperties {
        if (dischargedRetention == null || dischargedRetention.toSeconds() <= 0) {
            throw new IllegalArgumentException("adapters.urgency-state.discharged-retention must be at least 1s");
        }
    }
}
