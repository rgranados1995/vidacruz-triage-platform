package com.vidacruz.assistant.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes an {@link ApiError} as {@code application/problem+json}. Shared by the error handler and the security
 * entry point so a 401 looks like any other error. A 503 also says when to ask again ({@code Retry-After}).
 */
@Component
@EnableConfigurationProperties(ErrorResponseProperties.class)
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;
    private final String retryAfterSeconds;

    public ApiErrorWriter(ObjectMapper objectMapper, ErrorResponseProperties properties) {
        this.objectMapper = objectMapper;
        this.retryAfterSeconds = String.valueOf(properties.unavailableRetryAfter().toSeconds());
    }

    public Mono<Void> write(ServerWebExchange exchange, ApiError error) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(error.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (error.status() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, retryAfterSeconds);
        }
        return response.writeWith(Mono.fromCallable(() ->
                response.bufferFactory().wrap(objectMapper.writeValueAsBytes(error))));
    }
}
