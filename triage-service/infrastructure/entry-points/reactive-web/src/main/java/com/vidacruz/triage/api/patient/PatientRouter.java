package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.config.RouteProperties;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Routes of RF-01 and RF-02 with their OpenAPI documentation. No content-type predicate: a body that is not JSON
 * reaches the handler and is answered with 415 instead of a misleading 404.
 */
@Configuration
@EnableConfigurationProperties(RouteProperties.class)
public class PatientRouter {

    @Bean
    public RouterFunction<ServerResponse> patientRoutes(PatientHandler handler, RouteProperties routes) {
        return SpringdocRouteBuilder.route()
                .POST(routes.patients(), handler::register, PatientApiDocs::register)
                .PATCH(routes.patientStatus(), handler::changeStatus, PatientApiDocs::changeStatus)
                .GET(routes.patient(), handler::get, PatientApiDocs::get)
                .build();
    }
}
