package com.vidacruz.triage.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A background job that runs rounds at a fixed interval while the application is up. Rounds never overlap: a tick
 * that arrives while a round is still running is dropped (backpressure), and a failed round is logged and retried on
 * the next tick. It starts after every other bean and stops first, so it never works while the pools are closing.
 */
public abstract class PeriodicJob implements SmartLifecycle {

    /** Logger of the concrete job, so its level can be set per job. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String name;
    private final Duration interval;
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();

    protected PeriodicJob(String name, Duration interval) {
        this.name = name;
        this.interval = interval;
    }

    /** One round of work; emits how many items it processed. */
    protected abstract Mono<Long> runRound();

    /** Logs a round that processed items; at DEBUG unless the job overrides it. */
    protected void logRound(long processed) {
        log.atDebug().addKeyValue("processed", processed).log(name + " round");
    }

    @Override
    public void start() {
        subscription.set(Flux.interval(interval)
                .onBackpressureDrop()
                .concatMap(tick -> safeRound(), 1)
                .subscribe());
    }

    @Override
    public void stop() {
        var running = subscription.getAndSet(null);
        if (running != null) {
            running.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        var running = subscription.get();
        return running != null && !running.isDisposed();
    }

    private Mono<Void> safeRound() {
        return runRound()
                .filter(processed -> processed > 0)
                .doOnNext(this::logRound)
                .then()
                .onErrorResume(error -> {
                    log.atError().setCause(error).log(name + " round failed; retrying on the next tick");
                    return Mono.empty();
                });
    }
}
