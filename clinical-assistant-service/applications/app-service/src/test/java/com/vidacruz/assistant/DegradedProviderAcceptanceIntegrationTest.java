package com.vidacruz.assistant;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The resilience policy as configured in application.yaml, against a provider that is down: the transient failure
 * is retried once, the circuit opens and then answers 503 without calling the provider, and the service stays ready.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
class DegradedProviderAcceptanceIntegrationTest {

    @RegisterExtension
    static final WireMockExtension ANTHROPIC = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String MESSAGES = "/v1/messages";
    private static final String SEPSIS = "¿Cuándo se administra el primer antibiótico en sepsis?";

    @Autowired
    private WebTestClient client;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry, "integration-test-anthropic-key", ANTHROPIC.baseUrl());
    }

    @BeforeEach
    void providerIsDown() {
        circuitBreakers.circuitBreaker("claude").reset();
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(aResponse().withStatus(529)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"down\"}}")));
    }

    @Test
    void shouldRetryOnceAndAnswerServiceUnavailableWithRetryAfter() {
        consult().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody().jsonPath("$.codigo").isEqualTo("AST-002");

        ANTHROPIC.verify(2, postRequestedFor(urlPathEqualTo(MESSAGES)));
    }

    @Test
    void shouldStopCallingTheProviderOnceTheCircuitOpensAndStayReady() {
        for (var round = 0; round < 3; round++) {
            consult().expectStatus().isEqualTo(503);
        }
        assertThat(circuitBreakers.circuitBreaker("claude").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        ANTHROPIC.verify(5, postRequestedFor(urlPathEqualTo(MESSAGES)));

        consult().expectStatus().isEqualTo(503).expectHeader().valueEquals("Retry-After", "30");
        ANTHROPIC.verify(5, postRequestedFor(urlPathEqualTo(MESSAGES)));

        // The degraded mode is not an unhealthy pod: it keeps answering the notice and the 503
        client.get().uri("/health/readiness").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        client.post().uri("/asistente/consulta").header("X-API-Key", TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?"))
                .exchange()
                .expectStatus().isOk();
    }

    private WebTestClient.ResponseSpec consult() {
        return client.post().uri("/asistente/consulta").header("X-API-Key", TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("pregunta", SEPSIS))
                .exchange();
    }
}
