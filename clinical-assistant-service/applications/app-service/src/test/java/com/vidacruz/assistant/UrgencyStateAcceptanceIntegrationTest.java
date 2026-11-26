package com.vidacruz.assistant;

import com.vidacruz.assistant.kafka.consumer.TriageEventsConsumer;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import com.vidacruz.assistant.model.urgency.UrgencyState;
import com.vidacruz.assistant.usecase.urgencystate.GetUrgencyStateUseCase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-04 part B end to end: events of the triage published on a real Kafka, the real consumer and the projection in a
 * real Redis, read through the HTTP endpoint. The assistant never calls the triage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
class UrgencyStateAcceptanceIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static KafkaProducer<String, String> producer;

    @Autowired
    private WebTestClient client;
    @Autowired
    private GetUrgencyStateUseCase getUrgencyState;
    @Autowired
    private TriageEventsConsumer consumer;
    @Autowired
    private ReactiveStringRedisTemplate redis;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry, "", "http://localhost:9");
    }

    @BeforeAll
    static void connectToKafka() {
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, TestEnvironment.kafkaBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @AfterAll
    static void disconnect() {
        producer.close();
    }

    @Test
    void shouldFollowThePatientsOfTheEmergencyRoom() throws Exception {
        var before = await(state -> true);
        var waiting = UUID.randomUUID();
        var attended = UUID.randomUUID();
        var discharged = UUID.randomUUID();
        var start = now();

        publish(waiting, event("paciente_registrado", new Change(waiting, "CRITICO", "REGISTRADO", start)));
        publish(waiting, event("paciente_registrado", new Change(waiting, "CRITICO", "REGISTRADO", start)));
        publish(attended, "{this is not an event");
        publish(attended, event("paciente_registrado", new Change(attended, "URGENTE", "REGISTRADO", start)));
        publish(attended, event("paciente_en_consulta",
                new Change(attended, "URGENTE", "EN_CONSULTA", start.plusSeconds(60))));
        publish(discharged, event("paciente_registrado", new Change(discharged, "NO_URGENTE", "REGISTRADO", start)));
        publish(discharged, event("paciente_alta",
                new Change(discharged, "NO_URGENTE", "ALTA", start.plusSeconds(120))));
        // A late, older event of the discharged patient must not bring it back
        publish(discharged, event("paciente_registrado",
                new Change(discharged, "NO_URGENTE", "REGISTRADO", start.minusSeconds(60))));

        // The duplicate counts once, the unreadable message does not block its partition and the late event is ignored
        await(state -> count(state, TriageLevel.CRITICAL) == count(before, TriageLevel.CRITICAL) + 1
                && count(state, TriageLevel.URGENT) == count(before, TriageLevel.URGENT) + 1
                && state.updatedAt().filter(start.plusSeconds(120)::equals).isPresent());
        var after = await(state -> true);
        assertThat(count(after, TriageLevel.NON_URGENT)).isEqualTo(count(before, TriageLevel.NON_URGENT));
        assertThat(after.totalActive()).isEqualTo(before.totalActive() + 2);
        assertThat(PrometheusScrape.value(client, "assistant_urgency_events_total", "result", "applied")).isPositive();
        assertThat(PrometheusScrape.value(client, "assistant_urgency_events_total", "result", "ignored_older"))
                .isPositive();
        assertThat(PrometheusScrape.value(client, "assistant_urgency_events_total", "result", "skipped"))
                .isPositive();
        // Metrics of the Kafka client: the lag of the consumer
        assertThat(PrometheusScrape.value(client, "kafka_consumer_fetch_manager_records_lag_max", "application",
                "clinical-assistant-service")).isNotNegative();

        client.get().uri("/asistente/estado-urgencias").header("X-API-Key", TestEnvironment.API_KEY).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalActivos").isEqualTo(after.totalActive())
                .jsonPath("$.porNivel.CRITICO").isEqualTo(count(after, TriageLevel.CRITICAL))
                .jsonPath("$.porNivel.URGENTE").isEqualTo(count(after, TriageLevel.URGENT))
                .jsonPath("$.actualizadoEn").isEqualTo(start.plusSeconds(120).toString());
    }

    @Test
    void shouldRebuildTheProjectionWhenRedisLosesIt() throws Exception {
        var patient = UUID.randomUUID();
        var before = await(state -> true);
        publish(patient, event("paciente_registrado", new Change(patient, "URGENTE_2", "REGISTRADO", now())));
        var expected = await(state -> count(state, TriageLevel.URGENT_2) == count(before, TriageLevel.URGENT_2) + 1);

        consumer.stop();
        StepVerifier.create(redis.execute(connection -> connection.serverCommands().flushAll()).then())
                .verifyComplete();
        await(state -> state.totalActive() == 0);
        consumer.start();

        // Empty projection: the consumer reads the topic again from the beginning and gets the same state
        var rebuilt = await(state -> state.activeByLevel().equals(expected.activeByLevel()));
        assertThat(rebuilt.totalActive()).isEqualTo(expected.totalActive());
    }

    @Test
    void shouldContinueTheTraceOfTheTriageWhileHandlingItsEvent(CapturedOutput output) throws Exception {
        var traceId = UUID.randomUUID().toString().replace("-", "");
        var unreadable = new ProducerRecord<>(TestEnvironment.EVENTS_TOPIC, UUID.randomUUID().toString(),
                "{traced but not an event");
        unreadable.headers().add("traceparent", ("00-" + traceId + "-00f067aa0ba902b7-01")
                .getBytes(StandardCharsets.UTF_8));
        producer.send(unreadable).get();

        // The warning of the skipped event is logged inside the trace that came in the record
        StepVerifier.create(Mono.fromCallable(output::getOut)
                        .filter(logged -> logged.lines().anyMatch(line -> line.contains("Triage event skipped")
                                && line.contains(traceId)))
                        .repeatWhenEmpty(rounds -> rounds.delayElements(Duration.ofMillis(200))))
                .expectNextCount(1)
                .expectComplete()
                .verify(WAIT);
    }

    /** Polls the projection (without blocking a reactive thread) until it matches. */
    private UrgencyState await(Predicate<UrgencyState> condition) {
        var matched = new UrgencyState[1];
        StepVerifier.create(Mono.defer(getUrgencyState::current)
                        .filter(condition)
                        .repeatWhenEmpty(rounds -> rounds.delayElements(Duration.ofMillis(200))))
                .consumeNextWith(state -> matched[0] = state)
                .expectComplete()
                .verify(WAIT);
        return matched[0];
    }

    private static long count(UrgencyState state, TriageLevel level) {
        return state.activeByLevel().get(level);
    }

    private static void publish(UUID patient, String value) throws Exception {
        producer.send(new ProducerRecord<>(TestEnvironment.EVENTS_TOPIC, patient.toString(), value)).get();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** What changed for a patient: its level, its status and when. */
    private record Change(UUID patient, String level, String status, Instant at) {
    }

    /** An event as the triage publishes it (CloudEvents 1.0, key = patient id). */
    private static String event(String type, Change change) {
        var patient = change.patient();
        var at = change.at();
        return """
                {"specversion": "1.0", "id": "%s", "source": "/vidacruz/triage-service", "type": "%s",
                 "subject": "%s", "time": "%s", "datacontenttype": "application/json",
                 "data": {"pacienteId": "%s", "nivel": "%s", "estadoAnterior": null, "estado": "%s",
                          "escalado": false, "slaMinutos": 10, "fechaRegistro": "%s", "ocurridoEn": "%s"}}
                """.formatted(UUID.randomUUID(), type, patient, at, patient, change.level(), change.status(), at, at);
    }
}
