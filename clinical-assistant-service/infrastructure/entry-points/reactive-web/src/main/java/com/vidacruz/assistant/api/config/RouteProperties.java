package com.vidacruz.assistant.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paths of the API, read from the configuration.
 *
 * @param consultation path of the clinical consultation (RF-04 part A)
 */
@ConfigurationProperties(prefix = "routes")
public record RouteProperties(String consultation) {
}
