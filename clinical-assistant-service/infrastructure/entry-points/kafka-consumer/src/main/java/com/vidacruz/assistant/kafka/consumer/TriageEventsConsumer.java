package com.vidacruz.assistant.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vidacruz.assistant.usecase.urgencystate.ProjectTriageEventUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.MicrometerConsumerListener;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverPartition;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.receiver.observation.KafkaReceiverObservation;
import reactor.kafka.receiver.observation.KafkaRecordReceiverContext;
import reactor.util.retry.Retry;

import java.time.DateTimeException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the projection of the emergency room from {@code triaje.eventos} (RF-04 part B), never calling the triage.
 * <ul>
 *     <li>The events of each partition are applied in order (the key is the patient, so the events of a patient are
 *     too); the partitions run in parallel.</li>
 *     <li>An offset is committed only after its event is stored: at least once, and the projection is idempotent.</li>
 *     <li>A message that cannot be read, or of an unknown type, is logged and skipped, so it never blocks its
 *     partition.</li>
 *     <li>If Kafka or Redis fails, the consumer starts again with a growing pause from the last committed offset.</li>
 *     <li>When the projection is empty, the first assignment reads the topic from the beginning to rebuild it.</li>
 *     <li>Each event is handled inside an observation that continues the trace of the request of the triage that
 *     produced it ({@code traceparent} header), so both services show up in the same trace.</li>
 * </ul>
 */
@Slf4j
@Component
@EnableConfigurationProperties(TriageEventsProperties.class)
public class TriageEventsConsumer implements SmartLifecycle {

    private static final Duration FIRST_RESTART_PAUSE = Duration.ofSeconds(1);
    private static final Duration MAX_RESTART_PAUSE = Duration.ofSeconds(30);
    private static final Duration COMMIT_INTERVAL = Duration.ofSeconds(1);
    /** Name of this receiver in its spans. */
    static final String RECEIVER_ID = "urgency-state-projection";

    private final ProjectTriageEventUseCase projectTriageEvent;
    private final TriageEventsProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final UrgencyEventsMetrics metrics;
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();

    public TriageEventsConsumer(ProjectTriageEventUseCase projectTriageEvent, TriageEventsProperties properties,
                                MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.projectTriageEvent = projectTriageEvent;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        this.metrics = new UrgencyEventsMetrics(meterRegistry);
    }

    @Override
    public void start() {
        subscription.set(consume()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, FIRST_RESTART_PAUSE)
                        .maxBackoff(MAX_RESTART_PAUSE)
                        .doBeforeRetry(signal -> log.atWarn()
                                .addKeyValue("attempt", signal.totalRetries() + 1)
                                .setCause(signal.failure())
                                .log("Triage events consumer failed; starting again")))
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

    Flux<ReceiverOffset> consume() {
        return projectTriageEvent.needsRebuild()
                // Creating the Kafka consumer blocks (configuration files, DNS): never on the Redis event loop
                .publishOn(Schedulers.boundedElastic())
                .flatMapMany(rebuild -> KafkaReceiver.create(options(rebuild)).receive())
                .groupBy(received -> received.receiverOffset().topicPartition())
                .flatMap(partition -> partition.concatMap(this::handle));
    }

    /** Handles one event inside its observation, child of the trace that came in the record. */
    Mono<ReceiverOffset> handle(ReceiverRecord<String, String> received) {
        return Mono.defer(() -> {
            var observation = KafkaReceiverObservation.RECEIVER_OBSERVATION.start(null,
                    KafkaReceiverObservation.DefaultKafkaReceiverObservationConvention.INSTANCE,
                    () -> new KafkaRecordReceiverContext(received, RECEIVER_ID, properties.bootstrapServers()),
                    observationRegistry);
            return project(received)
                    .doOnError(observation::error)
                    .doFinally(signal -> observation.stop())
                    .contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, observation));
        });
    }

    /** Applies one event (or skips it when unreadable) and then marks its offset to be committed. */
    private Mono<ReceiverOffset> project(ReceiverRecord<String, String> received) {
        var offset = received.receiverOffset();
        return Mono.fromCallable(() -> TriageEventParser.parse(received.value()))
                .flatMap(Mono::justOrEmpty)
                .flatMap(projectTriageEvent::project)
                .doOnNext(metrics::projected)
                .switchIfEmpty(Mono.fromRunnable(() -> skip(offset, "event type not projected")))
                .onErrorResume(TriageEventsConsumer::isUnreadable, error -> Mono.fromRunnable(
                        () -> skip(offset, error.getMessage())))
                .then(Mono.fromRunnable(offset::acknowledge))
                .thenReturn(offset);
    }

    ReceiverOptions<String, String> options(boolean rebuild) {
        var firstAssignment = new AtomicBoolean(true);
        return ReceiverOptions.<String, String>create(Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, properties.groupId(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))
                .commitInterval(COMMIT_INTERVAL)
                // Metrics of the Kafka client itself, such as the lag of the consumer (records-lag-max)
                .consumerListener(new MicrometerConsumerListener(meterRegistry))
                .subscription(List.of(properties.topic()))
                .addAssignListener(partitions -> {
                    if (rebuild && firstAssignment.getAndSet(false)) {
                        rebuildFromTheBeginning(partitions);
                    }
                });
    }

    private void rebuildFromTheBeginning(Iterable<ReceiverPartition> partitions) {
        partitions.forEach(ReceiverPartition::seekToBeginning);
        log.atWarn().addKeyValue("topic", properties.topic())
                .log("Urgency state projection is empty; rebuilding it from the beginning of the topic");
        projectTriageEvent.rebuildStarted().subscribe(
                null, error -> log.atError().setCause(error).log("Could not mark the projection as rebuilt"));
    }

    /** Not JSON, a missing field, or a value outside the contract (id, level, status or date). */
    private static boolean isUnreadable(Throwable error) {
        return error instanceof JsonProcessingException || error instanceof IllegalArgumentException
                || error instanceof DateTimeException;
    }

    private void skip(ReceiverOffset offset, String reason) {
        metrics.skipped();
        log.atWarn()
                .addKeyValue("partition", offset.topicPartition().partition())
                .addKeyValue("offset", offset.offset())
                .addKeyValue("reason", reason)
                .log("Triage event skipped");
    }
}
