package com.vidacruz.assistant.api.error;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the error responses.
 *
 * @param unavailableRetryAfter value of {@code Retry-After} in a 503: when it makes sense to ask again (the time the
 *                              circuit breaker of the AI provider stays open)
 */
@ConfigurationProperties(prefix = "errors")
public record ErrorResponseProperties(Duration unavailableRetryAfter) {

    public ErrorResponseProperties {
        if (unavailableRetryAfter == null || unavailableRetryAfter.toSeconds() <= 0) {
            throw new IllegalArgumentException("errors.unavailable-retry-after must be at least one second");
        }
    }
}
