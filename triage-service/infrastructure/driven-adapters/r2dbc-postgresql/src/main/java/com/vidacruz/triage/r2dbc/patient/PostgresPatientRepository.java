package com.vidacruz.triage.r2dbc.patient;

import com.vidacruz.triage.model.audit.AuditEntry;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.r2dbc.alert.AlertWriter;
import com.vidacruz.triage.r2dbc.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * PostgreSQL storage of patients over R2DBC. Each change, its audit entry, its outbox event and its alert are written
 * in one transaction, and a change applies only if nobody changed the status in between (the stored status is part of
 * the update condition).
 */
@Repository
@RequiredArgsConstructor
public class PostgresPatientRepository implements PatientRepository {

    static final String CONCURRENT_CHANGE = "el paciente cambió de estado mientras se procesaba la solicitud";

    private final DatabaseClient client;
    private final TransactionalOperator transactionalOperator;
    private final OutboxWriter outboxWriter;
    private final AlertWriter alertWriter;

    @Override
    public Mono<Patient> register(PatientChange registration) {
        return PatientBinder.bindNewPatient(client.sql(PatientSql.INSERT_PATIENT), registration.patient())
                .fetch().rowsUpdated()
                .then(record(registration))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Patient> update(PatientChange change) {
        return PatientBinder.bindChange(client.sql(PatientSql.UPDATE_STATUS), change)
                .map(PatientRowMapper::toPatient)
                .one()
                .switchIfEmpty(Mono.defer(() -> Mono.error(
                        new BusinessException(BusinessError.INVALID_STATUS_TRANSITION, CONCURRENT_CHANGE))))
                .flatMap(stored -> record(withStored(change, stored)))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Patient> escalate(PatientChange escalation) {
        return client.sql(PatientSql.ESCALATE)
                .bind("id", escalation.patient().getId())
                .map(PatientRowMapper::toPatient)
                .one()
                .flatMap(stored -> record(withStored(escalation, stored)))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Flux<Patient> findOverdue(Instant now, int limit) {
        return client.sql(PatientSql.SELECT_OVERDUE)
                .bind("now", now)
                .bind("limit", limit)
                .map(PatientRowMapper::toPatient)
                .all();
    }

    @Override
    public Mono<Patient> findById(UUID id) {
        return client.sql(PatientSql.SELECT_BY_ID)
                .bind("id", id)
                .map(PatientRowMapper::toPatient)
                .one();
    }

    /** Audit entry, outbox event and alert of an applied change, inside the transaction of the caller. */
    private Mono<Patient> record(PatientChange applied) {
        return insertAudit(applied.auditEntry())
                .then(outboxWriter.write(applied))
                .then(alertWriter.recordFor(applied))
                .thenReturn(applied.patient());
    }

    /** The same change with the patient as stored, which may carry flags changed meanwhile by others. */
    private static PatientChange withStored(PatientChange change, Patient stored) {
        return new PatientChange(stored, change.previousStatus(), change.auditEntry());
    }

    private Mono<Long> insertAudit(AuditEntry entry) {
        return PatientBinder.bindAudit(client.sql(PatientSql.INSERT_AUDIT), entry)
                .fetch().rowsUpdated();
    }
}
