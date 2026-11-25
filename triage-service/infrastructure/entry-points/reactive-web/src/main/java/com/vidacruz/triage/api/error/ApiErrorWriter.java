package com.vidacruz.triage.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes an {@link ApiError} as {@code application/problem+json}. Shared by the error handler and the security
 * entry point so a 401 looks like any other error.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, ApiError error) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(error.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return response.writeWith(Mono.fromCallable(() ->
                response.bufferFactory().wrap(objectMapper.writeValueAsBytes(error))));
    }
}
