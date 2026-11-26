package com.vidacruz.triage.r2dbc.alert;

import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.alert.gateways.MedicalAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * PostgreSQL storage of the escalation alerts over R2DBC; the changes come from the notifications of every replica.
 */
@Repository
@RequiredArgsConstructor
public class PostgresMedicalAlertRepository implements MedicalAlertRepository {

    private final DatabaseClient client;
    private final TransactionalOperator transactionalOperator;
    private final AlertChangeListener listener;

    @Override
    public Flux<MedicalAlert> findActive() {
        return client.sql(AlertSql.SELECT_ACTIVE)
                .map(AlertRowMapper::toAlert)
                .all();
    }

    @Override
    public Mono<MedicalAlert> findById(long id) {
        return client.sql(AlertSql.SELECT_BY_ID)
                .bind("id", id)
                .map(AlertRowMapper::toAlert)
                .one();
    }

    @Override
    public Mono<MedicalAlert> acknowledge(long id, String acknowledgedBy, Instant at) {
        return client.sql(AlertSql.ACKNOWLEDGE)
                .bind("id", id)
                .bind("acknowledgedBy", acknowledgedBy)
                .bind("acknowledgedAt", at)
                .bind("updatedAt", at)
                .map(AlertRowMapper::toAlert)
                .one()
                .flatMap(stored -> AlertNotifications.send(client, stored.getId()).thenReturn(stored))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Flux<MedicalAlert> changes() {
        return listener.changes();
    }
}
