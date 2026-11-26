package com.vidacruz.assistant.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paths of the API, read from the configuration.
 *
 * @param consultation path of the clinical consultation (RF-04 part A)
 * @param urgencyState path of the state of the emergency room (RF-04 part B)
 */
@ConfigurationProperties(prefix = "routes")
public record RouteProperties(String consultation, String urgencyState) {
}
