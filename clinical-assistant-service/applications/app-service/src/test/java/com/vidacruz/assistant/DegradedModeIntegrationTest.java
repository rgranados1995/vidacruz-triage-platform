package com.vidacruz.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real application without a key of the AI provider: it starts and is ready, the corpus search still works and
 * the questions that need the model are answered with 503 (degraded mode).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
@AutoConfigureObservability
class DegradedModeIntegrationTest {

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry, "", "http://localhost:9");
    }

    @Test
    void shouldStartReadyAndExposeItsProbesAndMetrics() {
        client.get().uri("/health/liveness").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/health/readiness").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/prometheus").exchange().expectStatus().isUnauthorized();
        client.get().uri("/prometheus").header("X-API-Key", TestEnvironment.API_KEY).exchange().expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("application=\"clinical-assistant-service\""));
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("/asistente/consulta", "consultarProtocolos"));
    }

    @Test
    void shouldAnswerServiceUnavailableWhenTheModelIsNeeded() {
        consult("¿Cuáles son las medidas iniciales ante una crisis hipertensiva?")
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("AST-002")
                .jsonPath("$.status").isEqualTo(503);
    }

    @Test
    void shouldStillAnswerTheNoticeWhenNoProtocolMatches() {
        consult("¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo("No existe un protocolo disponible en el corpus oficial para esta "
                        + "pregunta.")
                .jsonPath("$.fuentes.length()").isEqualTo(0);
    }

    private WebTestClient.ResponseSpec consult(String question) {
        return client.post().uri("/asistente/consulta").header("X-API-Key", TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pregunta\": \"" + question + "\"}")
                .exchange();
    }
}
