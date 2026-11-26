package com.vidacruz.triage.api.report;

import com.vidacruz.triage.api.config.RouteProperties;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Route of the operation report (RF-05) with its OpenAPI documentation.
 */
@Configuration
@EnableConfigurationProperties(RouteProperties.class)
public class ReportRouter {

    @Bean
    public RouterFunction<ServerResponse> reportRoutes(ReportHandler handler, RouteProperties routes) {
        return SpringdocRouteBuilder.route()
                .GET(routes.operationReport(), handler::operationReport, ReportApiDocs::operationReport)
                .build();
    }
}
