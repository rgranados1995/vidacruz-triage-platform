package com.vidacruz.triage.kafka;

import com.vidacruz.triage.model.outbox.OutboxMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import reactor.kafka.sender.SenderOptions;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publishes to a real Kafka broker and reads the records back.
 */
@Testcontainers
class KafkaEventPublisherIntegrationTest {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    private static final String TOPIC = "triaje.eventos.test";
    private static final String TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private static ReactiveKafkaProducerTemplate<String, String> producer;

    @BeforeAll
    static void createProducer() {
        producer = new ReactiveKafkaProducerTemplate<>(SenderOptions.create(Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all")));
    }

    @AfterAll
    static void closeProducer() {
        producer.close();
    }

    @Test
    void shouldPublishTheEventsOfAPatientInOrderWithItsKeyAndContentType() {
        var publisher = new KafkaEventPublisher(producer, new TriageEventsTopicProperties(TOPIC, 3, (short) 1));
        var patientId = UUID.randomUUID().toString();
        var registered = message(patientId, "paciente_registrado", TRACE_PARENT);
        var inConsultation = message(patientId, "paciente_en_consulta", null);

        StepVerifier.create(publisher.publish(registered).then(publisher.publish(inConsultation))).verifyComplete();

        var records = readAll(2);
        assertThat(records).extracting(ConsumerRecord::key).containsOnly(patientId);
        assertThat(records).extracting(ConsumerRecord::value)
                .containsExactly(registered.payload(), inConsultation.payload());
        assertThat(new String(records.getFirst().headers().lastHeader("content-type").value(),
                StandardCharsets.UTF_8)).isEqualTo("application/cloudevents+json; charset=UTF-8");
        assertThat(new String(records.getFirst().headers().lastHeader("traceparent").value(),
                StandardCharsets.UTF_8)).isEqualTo(TRACE_PARENT);
        assertThat(records.getLast().headers().lastHeader("traceparent")).isNull();
    }

    private static OutboxMessage message(String patientId, String type, String traceParent) {
        var eventId = UUID.randomUUID();
        return new OutboxMessage(eventId, patientId, type, "{\"id\":\"" + eventId + "\",\"type\":\"" + type + "\"}",
                Instant.now(), traceParent);
    }

    private static List<ConsumerRecord<String, String>> readAll(int expected) {
        var records = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(TOPIC));
            var deadline = Instant.now().plusSeconds(20);
            while (records.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }
}
