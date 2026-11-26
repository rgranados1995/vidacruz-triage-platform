package com.vidacruz.assistant.api.consultation;

import com.vidacruz.assistant.api.config.RouteProperties;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Route of the clinical consultation with its OpenAPI documentation.
 */
@Configuration
@EnableConfigurationProperties(RouteProperties.class)
public class ConsultationRouter {

    @Bean
    public RouterFunction<ServerResponse> consultationRoutes(ConsultationHandler handler, RouteProperties routes) {
        return SpringdocRouteBuilder.route()
                .POST(routes.consultation(), handler::consult, ConsultationApiDocs::consult)
                .build();
    }
}
