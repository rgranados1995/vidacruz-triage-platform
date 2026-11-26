package com.vidacruz.assistant.api.urgency;

import com.vidacruz.assistant.api.config.RouteProperties;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Route of the state of the emergency room with its OpenAPI documentation.
 */
@Configuration
@EnableConfigurationProperties(RouteProperties.class)
public class UrgencyStateRouter {

    @Bean
    public RouterFunction<ServerResponse> urgencyStateRoutes(UrgencyStateHandler handler, RouteProperties routes) {
        return SpringdocRouteBuilder.route()
                .GET(routes.urgencyState(), handler::current, UrgencyStateApiDocs::current)
                .build();
    }
}
