package com.vidacruz.triage.model.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * An event stored in the outbox in the same transaction as the change that produced it, waiting to be published.
 *
 * @param eventId    unique id of the event, so consumers can ignore a repeated delivery
 * @param key        partition key: events with the same key keep their order (the patient id)
 * @param type       event name of the contract
 * @param payload    message exactly as it is published (opaque to the domain)
 * @param occurredAt instant of the change
 */
public record OutboxMessage(UUID eventId, String key, String type, String payload, Instant occurredAt) {
}
