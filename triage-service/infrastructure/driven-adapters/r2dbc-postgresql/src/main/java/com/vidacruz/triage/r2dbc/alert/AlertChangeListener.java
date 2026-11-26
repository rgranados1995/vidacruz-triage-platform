package com.vidacruz.triage.r2dbc.alert;

import com.vidacruz.triage.model.alert.MedicalAlert;
import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hears the alert notifications of every replica on a connection of its own (outside the pool) and publishes the
 * changed alerts to the subscribers of this replica. If the connection drops it reconnects with a growing pause and,
 * once listening again, republishes the alerts changed while it was away, so no subscriber misses a change.
 */
@Slf4j
public class AlertChangeListener implements SmartLifecycle {

    /** Covers the clock differences between the replicas when looking for the changes missed while away. */
    private static final Duration MISSED_CHANGES_MARGIN = Duration.ofSeconds(5);

    private final ConnectionFactory listenConnections;
    private final DatabaseClient client;
    private final Clock clock;
    private final ReconnectBackoff backoff;
    /** Best effort per subscriber: each subscriber buffers on its own, so a slow one never holds the others back. */
    private final Sinks.Many<MedicalAlert> changes = Sinks.many().multicast().directBestEffort();
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();
    /** When the last connection was lost; null while listening normally. */
    private final AtomicReference<Instant> lostAt = new AtomicReference<>();

    /**
     * @param listenConnections connections to PostgreSQL that are not taken from the pool of the application
     */
    public AlertChangeListener(ConnectionFactory listenConnections, DatabaseClient client, Clock clock,
                               ReconnectBackoff backoff) {
        this.listenConnections = listenConnections;
        this.client = client;
        this.clock = clock;
        this.backoff = backoff;
    }

    /**
     * Pauses between reconnection attempts.
     *
     * @param first pause after the first failure
     * @param max   longest pause, reached by doubling the previous one
     */
    public record ReconnectBackoff(Duration first, Duration max) {
    }

    public Flux<MedicalAlert> changes() {
        return changes.asFlux();
    }

    @Override
    public void start() {
        subscription.set(listen()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, backoff.first())
                        .maxBackoff(backoff.max())
                        .doBeforeRetry(signal -> log.atWarn()
                                .addKeyValue("attempt", signal.totalRetries() + 1)
                                .setCause(signal.failure())
                                .log("Alert notifications lost; reconnecting")))
                .subscribe(changes::tryEmitNext));
    }

    @Override
    public void stop() {
        var running = subscription.getAndSet(null);
        if (running != null) {
            running.dispose();
        }
        changes.tryEmitComplete();
    }

    @Override
    public boolean isRunning() {
        var running = subscription.get();
        return running != null && !running.isDisposed();
    }

    /** One connection: listens, republishes what was missed and then follows the notifications until it drops. */
    private Flux<MedicalAlert> listen() {
        return Flux.usingWhen(Mono.from(listenConnections.create()).cast(PostgresqlConnection.class),
                        connection -> connection.createStatement(AlertSql.LISTEN).execute()
                                .flatMap(PostgresqlResult::getRowsUpdated)
                                .doOnComplete(() -> log.info("Listening to the alert notifications"))
                                .thenMany(Flux.merge(notified(connection), missedWhileAway())),
                        PostgresqlConnection::close)
                // A connection that ends without an error also has to be replaced
                .concatWith(Flux.error(() -> new IllegalStateException("The alert notifications ended")))
                .doOnError(error -> lostAt.compareAndSet(null, clock.instant()));
    }

    private Flux<MedicalAlert> notified(PostgresqlConnection connection) {
        return connection.getNotifications()
                .map(Notification::getParameter)
                .map(Long::valueOf)
                .concatMap(this::findById);
    }

    private Flux<MedicalAlert> missedWhileAway() {
        // Read on every connection, not when the flow is built: retries subscribe to the same flow again
        return Mono.fromSupplier(lostAt::get)
                .flatMapMany(since -> client.sql(AlertSql.SELECT_CHANGED_SINCE)
                        .bind("since", since.minus(MISSED_CHANGES_MARGIN))
                        .map(AlertRowMapper::toAlert)
                        .all()
                        .doOnComplete(() -> lostAt.set(null)));
    }

    private Mono<MedicalAlert> findById(long alertId) {
        return client.sql(AlertSql.SELECT_BY_ID)
                .bind("id", alertId)
                .map(AlertRowMapper::toAlert)
                .one();
    }
}
