package com.vidacruz.triage.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topic of the triage events.
 *
 * @param name       topic name ({@code triaje.eventos} in the statement)
 * @param partitions partitions; the events of a patient always go to the same one (the key is the patient id)
 * @param replicas   replication factor
 */
@ConfigurationProperties(prefix = "adapters.kafka.triage-events")
public record TriageEventsTopicProperties(String name, int partitions, short replicas) {
}
