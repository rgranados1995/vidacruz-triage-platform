package com.vidacruz.triage.r2dbc.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vidacruz.triage.contract.ContractCodes;
import com.vidacruz.triage.contract.event.PatientEvents;
import com.vidacruz.triage.model.patient.PatientChange;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes the event of a patient change into the outbox. It must run inside the transaction of the change, so the
 * change and its event are stored together or not at all. The W3C trace context of the current request is stored
 * next to the event, so the relay can hand it to the consumers even though it publishes later, outside the request.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    static final String TRACEPARENT = "traceparent";

    private final DatabaseClient client;
    private final Tracer tracer;
    private final Propagator propagator;

    public Mono<Long> write(PatientChange change) {
        return Mono.fromCallable(() -> newEvent(change))
                .flatMap(event -> client.sql(OutboxSql.INSERT)
                        .bind("eventId", event.eventId())
                        .bind("aggregateId", change.patient().getId())
                        .bind("eventType", ContractCodes.eventType(change.auditEntry().eventType()))
                        .bind("payload", Json.of(event.payload()))
                        .bind("occurredAt", change.auditEntry().occurredAt())
                        .bind(TRACEPARENT, Parameters.in(R2dbcType.VARCHAR, event.traceParent()))
                        .fetch().rowsUpdated());
    }

    private NewEvent newEvent(PatientChange change) throws JsonProcessingException {
        var eventId = UUID.randomUUID();
        return new NewEvent(eventId, PatientEvents.toJson(change, eventId), currentTraceParent());
    }

    /** The trace context as the W3C header; null outside a trace. */
    private String currentTraceParent() {
        return Optional.ofNullable(tracer.currentTraceContext().context())
                .map(this::traceParentOf)
                .orElse(null);
    }

    private String traceParentOf(TraceContext context) {
        var carrier = new HashMap<String, String>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    private record NewEvent(UUID eventId, String payload, String traceParent) {
    }
}
