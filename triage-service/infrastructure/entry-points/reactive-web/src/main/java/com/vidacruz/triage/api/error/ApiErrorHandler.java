package com.vidacruz.triage.api.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Global error handler: every failure of a request ends here and leaves as RFC 9457. Runs before the default handler
 * of Spring Boot. Client errors are logged as WARN with their code; unexpected ones as ERROR with the stack trace.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class ApiErrorHandler implements WebExceptionHandler {

    private static final int FIRST_SERVER_ERROR = 500;

    private final ApiErrorWriter writer;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        return Mono.just(exchange.getResponse())
                .filter(response -> !response.isCommitted())
                .switchIfEmpty(Mono.defer(() -> Mono.error(error)))
                .map(response -> ApiErrors.from(error, exchange.getRequest().getPath().value()))
                .doOnNext(apiError -> log(apiError, error))
                .flatMap(apiError -> writer.write(exchange, apiError));
    }

    private static void log(ApiError apiError, Throwable error) {
        var event = apiError.status() >= FIRST_SERVER_ERROR ? log.atError().setCause(error) : log.atWarn();
        event.addKeyValue("errorCode", apiError.code())
                .addKeyValue("httpStatus", apiError.status())
                .addKeyValue("path", apiError.instance())
                .log("Request failed");
    }
}
