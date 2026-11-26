package com.vidacruz.assistant.kafka.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the events of the triage are read from.
 *
 * @param bootstrapServers Kafka brokers
 * @param topic            topic of the triage events ({@code triaje.eventos})
 * @param groupId          consumer group of the projection of the emergency room
 */
@ConfigurationProperties(prefix = "adapters.kafka.triage-events")
public record TriageEventsProperties(String bootstrapServers, String topic, String groupId) {

    public TriageEventsProperties {
        if (isBlank(bootstrapServers) || isBlank(topic) || isBlank(groupId)) {
            throw new IllegalArgumentException("adapters.kafka.triage-events needs bootstrap-servers, topic and "
                    + "group-id");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
