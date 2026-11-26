package com.vidacruz.triage.r2dbc.alert;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

/**
 * Tells every replica that an alert changed. Inside a transaction, PostgreSQL holds the notification until the
 * commit and drops it on a rollback, so nobody hears of a change that was not stored.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AlertNotifications {

    static Mono<Void> send(DatabaseClient client, long alertId) {
        return client.sql(AlertSql.NOTIFY).bind("id", alertId).then();
    }
}
