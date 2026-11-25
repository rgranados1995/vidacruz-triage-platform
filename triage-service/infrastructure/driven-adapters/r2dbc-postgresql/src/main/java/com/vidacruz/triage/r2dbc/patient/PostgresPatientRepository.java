package com.vidacruz.triage.r2dbc.patient;

import com.vidacruz.triage.model.audit.AuditEntry;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * PostgreSQL storage of patients over R2DBC. Each change and its audit entry are written in one transaction, and a
 * change applies only if nobody changed the status in between (the stored status is part of the update condition).
 */
@Repository
@RequiredArgsConstructor
public class PostgresPatientRepository implements PatientRepository {

    static final String CONCURRENT_CHANGE = "el paciente cambió de estado mientras se procesaba la solicitud";

    private final DatabaseClient client;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<Patient> register(PatientChange registration) {
        return PatientBinder.bindNewPatient(client.sql(PatientSql.INSERT_PATIENT), registration.patient())
                .fetch().rowsUpdated()
                .then(insertAudit(registration.auditEntry()))
                .thenReturn(registration.patient())
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Patient> update(PatientChange change) {
        return PatientBinder.bindChange(client.sql(PatientSql.UPDATE_PATIENT), change)
                .fetch().rowsUpdated()
                .filter(rows -> rows == 1L)
                .switchIfEmpty(Mono.defer(() -> Mono.error(
                        new BusinessException(BusinessError.INVALID_STATUS_TRANSITION, CONCURRENT_CHANGE))))
                .then(insertAudit(change.auditEntry()))
                .thenReturn(change.patient())
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Patient> findById(UUID id) {
        return client.sql(PatientSql.SELECT_BY_ID)
                .bind("id", id)
                .map(PatientRowMapper::toPatient)
                .one();
    }

    private Mono<Long> insertAudit(AuditEntry entry) {
        return PatientBinder.bindAudit(client.sql(PatientSql.INSERT_AUDIT), entry)
                .fetch().rowsUpdated();
    }
}
