package com.vidacruz.triage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the triage topic from the beginning with a fresh consumer group, keeping the events of one patient.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TriageTopicReader {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Stops as soon as {@code expected} events of the patient arrived, or when {@code maxWait} passed. */
    static List<JsonNode> eventsOf(String patientId, int expected, Duration maxWait) throws JsonProcessingException {
        var values = new ArrayList<String>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestEnvironment.kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(TestEnvironment.EVENTS_TOPIC));
            var deadline = Instant.now().plus(maxWait);
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
            events.add(JSON.readTree(value));
        }
        return events;
    }
}
