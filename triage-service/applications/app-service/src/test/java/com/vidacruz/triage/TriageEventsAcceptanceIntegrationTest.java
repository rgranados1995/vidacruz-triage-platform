package com.vidacruz.triage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-01 and RF-02 events end to end: the HTTP changes of a patient reach the topic through the outbox relay, in
 * order, as CloudEvents without personal data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
@AutoConfigureObservability
class TriageEventsAcceptanceIntegrationTest {

    private static final String PATIENTS = "/triage/pacientes";
    private static final String KEY_HEADER = "X-API-Key";
    private static final String REQUEST_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String REQUEST_TRACE_PARENT = "00-" + REQUEST_TRACE_ID + "-00f067aa0ba902b7-01";

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @Test
    void shouldPublishTheEventsOfAPatientInOrderAsCloudEvents() throws Exception {
        var classifiedBefore = PrometheusScrape.counter(client, "triage_patients_classified_total", "NON_URGENT");
        var id = register();
        assertThat(PrometheusScrape.counter(client, "triage_patients_classified_total", "NON_URGENT"))
                .isEqualTo(classifiedBefore + 1);
        changeStatus(id, "EN_CONSULTA");
        changeStatus(id, "ALTA");

        var events = TriageTopicReader.eventsOf(id, 3, Duration.ofSeconds(30));

        assertThat(events).extracting(event -> event.get("type").asText())
                .containsExactly("paciente_registrado", "paciente_en_consulta", "paciente_alta");
        assertThat(events).extracting(event -> event.get("data").get("estado").asText())
                .containsExactly("REGISTRADO", "EN_CONSULTA", "ALTA");
        var registered = events.getFirst();
        assertThat(registered.get("specversion").asText()).isEqualTo("1.0");
        assertThat(registered.get("subject").asText()).isEqualTo(id);
        assertThat(registered.get("data").get("nivel").asText()).isEqualTo("NO_URGENTE");
        assertThat(registered.toString()).doesNotContain("Paciente de eventos", "Control de eventos");
        assertThat(events).extracting(event -> event.get("id").asText()).doesNotHaveDuplicates();
    }

    @Test
    void shouldHandTheTraceOfTheRequestToTheConsumersOfItsEvent() {
        var id = register(REQUEST_TRACE_PARENT);

        var records = TriageTopicReader.recordsOf(id, 1, Duration.ofSeconds(30));

        // Same trace as the request, even though the relay publishes it later from another thread
        assertThat(records).singleElement().satisfies(published -> assertThat(
                new String(published.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8))
                .matches("00-" + REQUEST_TRACE_ID + "-[0-9a-f]{16}-01"));
    }

    private String register() {
        return register("00-" + UUID.randomUUID().toString().replace("-", "") + "-00f067aa0ba902b7-01");
    }

    private String register(String traceParent) {
        var created = client.post().uri(PATIENTS).header(KEY_HEADER, TestEnvironment.API_KEY)
                .header("traceparent", traceParent)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"nombreCompleto": "Paciente de eventos", "edad": 40, "frecuenciaCardiaca": 72,
                         "presionSistolica": 120, "presionDiastolica": 80, "spo2": 99, "temperatura": 36.5,
                         "dolor": 2, "gcs": 15, "quejaPrincipal": "Control de eventos"}""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
        return String.valueOf(Objects.requireNonNull(created).get("id"));
    }

    private void changeStatus(String id, String status) {
        client.patch().uri(PATIENTS + "/" + id + "/estado").header(KEY_HEADER, TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"estado\": \"" + status + "\"}")
                .exchange()
                .expectStatus().isOk();
    }
}
