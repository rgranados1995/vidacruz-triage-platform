package com.vidacruz.triage.r2dbc.alert;

import com.vidacruz.triage.model.patient.PatientChange;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Keeps the alerts in step with the patient changes. It must run inside the transaction of the change: the
 * escalation raises the alert and the consultation or the discharge resolves it, together or not at all.
 */
@Component
@RequiredArgsConstructor
public class AlertWriter {

    private final DatabaseClient client;

    public Mono<Void> recordFor(PatientChange applied) {
        var entry = applied.auditEntry();
        return switch (entry.eventType()) {
            case ESCALATED -> notifyEach(raise(applied));
            case CONSULTATION_STARTED, DISCHARGED -> notifyEach(resolve(applied.patient().getId(), entry.occurredAt()));
            case REGISTERED -> Mono.empty();
        };
    }

    private Flux<Long> raise(PatientChange escalation) {
        var patient = escalation.patient();
        var escalatedAt = escalation.auditEntry().occurredAt();
        return client.sql(AlertSql.RAISE)
                .bind("patientId", patient.getId())
                .bind("triageLevel", patient.getTriageLevel().name())
                .bind("slaMinutes", patient.getSlaMinutes())
                .bind("registeredAt", patient.getRegisteredAt())
                .bind("escalatedAt", escalatedAt)
                .bind("updatedAt", escalatedAt)
                .map(row -> row.get("id", Long.class))
                .all();
    }

    private Flux<Long> resolve(UUID patientId, Instant at) {
        return client.sql(AlertSql.RESOLVE)
                .bind("patientId", patientId)
                .bind("resolvedAt", at)
                .bind("updatedAt", at)
                .map(row -> row.get("id", Long.class))
                .all();
    }

    private Mono<Void> notifyEach(Flux<Long> alertIds) {
        return alertIds.concatMap(id -> AlertNotifications.send(client, id)).then();
    }
}
