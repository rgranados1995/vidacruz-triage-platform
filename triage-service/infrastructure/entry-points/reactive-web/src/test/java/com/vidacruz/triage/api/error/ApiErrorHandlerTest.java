package com.vidacruz.triage.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
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

        StepVerifier.create(handler.handle(exchange, new BusinessException(BusinessError.PATIENT_NOT_FOUND)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("\"codigo\":\"TRG-002\"", "\"status\":404"))
                .verifyComplete();
        assertThat(output).contains("Request failed");
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
        return MockServerWebExchange.from(MockServerHttpRequest.get("/triage/pacientes/1").build());
    }
}
