package com.vidacruz.triage.scheduler;

import com.vidacruz.triage.usecase.publishpendingevents.PublishPendingEventsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the outbox relay while the application is up. Rounds never overlap: a tick that arrives while a round is
 * still publishing is dropped (backpressure), and a failed round is logged and retried on the next tick. It starts
 * after every other bean and stops first, so it never publishes while the database pool is closing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxRelayJob implements SmartLifecycle {

    private final PublishPendingEventsUseCase publishPendingEvents;
    private final OutboxRelayProperties properties;
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();

    @Override
    public void start() {
        subscription.set(Flux.interval(properties.pollInterval())
                .onBackpressureDrop()
                .concatMap(tick -> relayOnce(), 1)
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

    private Mono<Void> relayOnce() {
        return publishPendingEvents.publishPending()
                .filter(published -> published > 0)
                .doOnNext(published -> log.atDebug().addKeyValue("published", published).log("Outbox relay round"))
                .then()
                .onErrorResume(error -> {
                    log.atError().setCause(error).log("Outbox relay round failed; retrying on the next tick");
                    return Mono.empty();
                });
    }
}
