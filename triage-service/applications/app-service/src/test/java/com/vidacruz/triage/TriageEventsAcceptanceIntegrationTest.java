package com.vidacruz.triage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-01 and RF-02 events end to end: the HTTP changes of a patient reach the topic through the outbox relay, in
 * order, as CloudEvents without personal data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
class TriageEventsAcceptanceIntegrationTest {

    private static final String PATIENTS = "/triage/pacientes";
    private static final String KEY_HEADER = "X-API-Key";

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @Test
    void shouldPublishTheEventsOfAPatientInOrderAsCloudEvents() throws Exception {
        var id = register();
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

    private String register() {
        var created = client.post().uri(PATIENTS).header(KEY_HEADER, TestEnvironment.API_KEY)
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
