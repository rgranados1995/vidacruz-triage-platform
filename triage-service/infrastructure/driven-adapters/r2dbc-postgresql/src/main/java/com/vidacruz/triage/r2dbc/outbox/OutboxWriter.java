package com.vidacruz.triage.r2dbc.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vidacruz.triage.contract.ContractCodes;
import com.vidacruz.triage.contract.event.PatientEvents;
import com.vidacruz.triage.model.patient.PatientChange;
import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Writes the event of a patient change into the outbox. It must run inside the transaction of the change, so the
 * change and its event are stored together or not at all.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final DatabaseClient client;

    public Mono<Long> write(PatientChange change) {
        return Mono.fromCallable(() -> newEvent(change))
                .flatMap(event -> client.sql(OutboxSql.INSERT)
                        .bind("eventId", event.eventId())
                        .bind("aggregateId", change.patient().getId())
                        .bind("eventType", ContractCodes.eventType(change.auditEntry().eventType()))
                        .bind("payload", Json.of(event.payload()))
                        .bind("occurredAt", change.auditEntry().occurredAt())
                        .fetch().rowsUpdated());
    }

    private static NewEvent newEvent(PatientChange change) throws JsonProcessingException {
        var eventId = UUID.randomUUID();
        return new NewEvent(eventId, PatientEvents.toJson(change, eventId));
    }

    private record NewEvent(UUID eventId, String payload) {
    }
}
