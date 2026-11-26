package com.vidacruz.assistant.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ApiErrorHandlerTest {

    private final ApiErrorHandler handler = new ApiErrorHandler(new ApiErrorWriter(new ObjectMapper()));

    @Test
    void shouldWriteTheErrorAsProblemDetailsAndLogItsCode(CapturedOutput output) {
        var exchange = exchange();

        var unavailable = new BusinessException(BusinessError.ASSISTANT_UNAVAILABLE, "",
                new IllegalStateException("read timed out"));

        StepVerifier.create(handler.handle(exchange, unavailable)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("\"codigo\":\"AST-002\"", "\"status\":503"))
                .verifyComplete();
        // The degraded mode is expected: a warning with the cause in one line, no stack trace
        assertThat(output).contains("Request failed", "WARN", "cause=\"IllegalStateException: read timed out\"")
                .doesNotContain("\tat ");
    }

    @Test
    void shouldLogAnUnexpectedFailureAsAnErrorWithItsStackTrace(CapturedOutput output) {
        StepVerifier.create(handler.handle(exchange(), new IllegalStateException("bug"))).verifyComplete();

        assertThat(output).contains("ERROR", "errorCode=\"AST-999\"", "java.lang.IllegalStateException: bug");
    }

    @Test
    void shouldFindTheDeepestCause() {
        var deepest = new IllegalArgumentException("deepest");

        assertThat(ApiErrorHandler.rootCause(new RuntimeException("top", new IllegalStateException("mid", deepest))))
                .isEqualTo("IllegalArgumentException: deepest");
        assertThat(ApiErrorHandler.rootCause(deepest)).isEqualTo("IllegalArgumentException: deepest");
    }

    @Test
    void shouldLeaveTheErrorToTheServerWhenTheResponseWasAlreadySent() {
        var exchange = exchange();
        var failure = new IllegalStateException("failed after the response started");
        StepVerifier.create(exchange.getResponse().setComplete()).verifyComplete();

        StepVerifier.create(handler.handle(exchange, failure))
                .expectErrorMatches(error -> error == failure)
                .verify();
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/asistente/consulta").build());
    }
}
