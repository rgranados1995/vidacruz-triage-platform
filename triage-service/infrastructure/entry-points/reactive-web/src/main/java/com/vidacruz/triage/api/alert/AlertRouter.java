package com.vidacruz.triage.api.alert;

import com.vidacruz.triage.api.config.RouteProperties;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Routes of the escalation alerts (RF-06) with their OpenAPI documentation.
 */
@Configuration
@EnableConfigurationProperties({RouteProperties.class, AlertStreamProperties.class})
public class AlertRouter {

    @Bean
    public RouterFunction<ServerResponse> alertRoutes(AlertHandler handler, RouteProperties routes) {
        return SpringdocRouteBuilder.route()
                .GET(routes.alerts(), handler::listActive, AlertApiDocs::listActive)
                .GET(routes.alertStream(), handler::stream, AlertApiDocs::stream)
                .POST(routes.alertAcknowledgement(), handler::acknowledge, AlertApiDocs::acknowledge)
                .build();
    }
}
