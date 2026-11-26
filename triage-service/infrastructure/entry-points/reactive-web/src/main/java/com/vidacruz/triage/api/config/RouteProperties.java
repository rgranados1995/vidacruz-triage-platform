package com.vidacruz.triage.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paths of the API, read from the configuration.
 *
 * @param patients        base path of the patients resource
 * @param operationReport path of the operation report (RF-05)
 * @param alerts          base path of the escalation alerts (RF-06)
 */
@ConfigurationProperties(prefix = "routes")
public record RouteProperties(String patients, String operationReport, String alerts) {

    public String patient() {
        return patients + "/{id}";
    }

    public String patientStatus() {
        return patients + "/{id}/estado";
    }

    public String alertStream() {
        return alerts + "/stream";
    }

    public String alertAcknowledgement() {
        return alerts + "/{id}/reconocimiento";
    }
}
