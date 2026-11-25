package com.vidacruz.triage.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paths of the API, read from the configuration.
 *
 * @param patients base path of the patients resource
 */
@ConfigurationProperties(prefix = "routes")
public record RouteProperties(String patients) {

    public String patient() {
        return patients + "/{id}";
    }

    public String patientStatus() {
        return patients + "/{id}/estado";
    }
}
