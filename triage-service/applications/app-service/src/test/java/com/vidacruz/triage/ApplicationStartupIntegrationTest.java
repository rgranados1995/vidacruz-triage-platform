package com.vidacruz.triage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real application against PostgreSQL: unit tests do not catch missing properties, migrations that do
 * not apply or broken auto-configuration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
@AutoConfigureObservability
class ApplicationStartupIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @Test
    void shouldReportLivenessUp() {
        webTestClient.get().uri("/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void shouldReportReadinessUpWhenTheDatabaseIsReachable() {
        webTestClient.get().uri("/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void shouldExposePrometheusMetricsTaggedWithTheApplication() {
        webTestClient.get().uri("/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("application=\"triage-service\""));
    }
}
