package com.vidacruz.triage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
class TriageEventsAcceptanceIntegrationTest {

    private static final String PATIENTS = "/triage/pacientes";
    private static final String KEY_HEADER = "X-API-Key";

    @Autowired
    private WebTestClient client;

    private final ObjectMapper json = new ObjectMapper();

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @Test
    void shouldPublishTheEventsOfAPatientInOrderAsCloudEvents() throws Exception {
        var id = register();
        changeStatus(id, "EN_CONSULTA");
        changeStatus(id, "ALTA");

        var events = eventsOf(id, 3);

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

    /** Reads the topic from the beginning until the expected events of the patient arrive (or 30 s pass). */
    private List<JsonNode> eventsOf(String patientId, int expected) throws Exception {
        var values = new ArrayList<String>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestEnvironment.kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(TestEnvironment.EVENTS_TOPIC));
            var deadline = Instant.now().plusSeconds(30);
            while (values.size() < expected && Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> consumed : consumer.poll(Duration.ofMillis(500))) {
                    if (patientId.equals(consumed.key())) {
                        values.add(consumed.value());
                    }
                }
            }
        }
        var events = new ArrayList<JsonNode>();
        for (var value : values) {
            events.add(json.readTree(value));
        }
        return events;
    }
}
