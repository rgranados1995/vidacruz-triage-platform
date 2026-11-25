package com.vidacruz.triage.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API key the clients send in the {@code X-API-Key} header. It comes from a secret of the environment, never from
 * the code.
 *
 * @param apiKey expected key; at least {@value #MINIMUM_LENGTH} characters so it cannot be guessed easily
 */
@ConfigurationProperties(prefix = "security")
public record ApiKeyProperties(String apiKey) {

    static final int MINIMUM_LENGTH = 16;

    public ApiKeyProperties {
        if (apiKey == null || apiKey.isBlank() || apiKey.length() < MINIMUM_LENGTH) {
            throw new IllegalArgumentException("security.api-key must have at least " + MINIMUM_LENGTH + " characters");
        }
    }
}
