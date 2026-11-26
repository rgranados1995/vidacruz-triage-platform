package com.vidacruz.assistant.kafka.consumer;

import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.PatientStatus;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import com.vidacruz.assistant.usecase.urgencystate.ProjectTriageEventUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.MicrometerConsumerListener;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverPartition;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.receiver.observation.KafkaRecordReceiverContext;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The consumer without Kafka: what it does with each kind of message and how it rebuilds the projection.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TriageEventsConsumerTest {

    private static final TopicPartition PARTITION = new TopicPartition("triaje.eventos", 1);
    private static final UUID PATIENT = UUID.fromString("3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12");
    private static final String OCCURRED = "2026-11-26T10:04:12.345678Z";
    private static final String TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final TriageEventsProperties PROPERTIES =
            new TriageEventsProperties("localhost:9092", "triaje.eventos", "assistant");

    @Mock
    private ProjectTriageEventUseCase projectTriageEvent;
    @Mock
    private ReceiverOffset offset;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<Observation.Context> observed = new CopyOnWriteArrayList<>();
    private final ObservationRegistry observations = ObservationRegistry.create();

    @BeforeEach
    void recordTheObservations() {
        observations.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStop(Observation.Context context) {
                observed.add(context);
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
    }

    @Test
    void shouldProjectTheEventAndThenCommitItsOffset() {
        var snapshot = new PatientSnapshot(PATIENT, TriageLevel.CRITICAL, PatientStatus.REGISTERED,
                Instant.parse(OCCURRED));
        when(projectTriageEvent.project(snapshot)).thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(consumer().handle(received(
                        TriageEventParserTest.event("paciente_registrado", "CRITICO", "REGISTRADO", OCCURRED))))
                .expectNext(offset)
                .verifyComplete();

        verify(offset).acknowledge();
        assertThat(events(UrgencyEventsMetrics.APPLIED)).isEqualTo(1.0);
        assertThat(events(UrgencyEventsMetrics.IGNORED_OLDER)).isZero();
    }

    @Test
    void shouldCountAnOlderEventAsIgnored() {
        when(projectTriageEvent.project(any())).thenReturn(Mono.just(Boolean.FALSE));

        StepVerifier.create(consumer().handle(received(
                        TriageEventParserTest.event("paciente_alta", "CRITICO", "ALTA", OCCURRED))))
                .expectNext(offset)
                .verifyComplete();

        assertThat(events(UrgencyEventsMetrics.IGNORED_OLDER)).isEqualTo(1.0);
        assertThat(events(UrgencyEventsMetrics.APPLIED)).isZero();
    }

    @Test
    void shouldHandleEachEventInsideAnObservationThatReadsTheTraceOfTheRecord() {
        when(projectTriageEvent.project(any())).thenReturn(Mono.just(Boolean.TRUE));
        var record = new ConsumerRecord<>(PARTITION.topic(), PARTITION.partition(), 42L, PATIENT.toString(),
                TriageEventParserTest.event("paciente_registrado", "CRITICO", "REGISTRADO", OCCURRED));
        record.headers().add("traceparent", TRACE_PARENT.getBytes(StandardCharsets.UTF_8));

        StepVerifier.create(consumer().handle(new ReceiverRecord<>(record, offset))).expectNext(offset)
                .verifyComplete();

        assertThat(observed).singleElement().isInstanceOfSatisfying(KafkaRecordReceiverContext.class, context -> {
            assertThat(context.getGetter().get(context.getCarrier(), "traceparent")).isEqualTo(TRACE_PARENT);
            assertThat(context.getError()).isNull();
        });
    }

    @Test
    void shouldRecordTheFailureInTheObservation() {
        var failure = new BusinessException(BusinessError.URGENCY_STATE_UNAVAILABLE, "");
        when(projectTriageEvent.project(any())).thenReturn(Mono.error(failure));

        StepVerifier.create(consumer().handle(received(
                        TriageEventParserTest.event("paciente_alta", "CRITICO", "ALTA", OCCURRED))))
                .verifyErrorMatches(error -> error == failure);

        assertThat(observed).singleElement().satisfies(context -> assertThat(context.getError()).isSameAs(failure));
    }

    @Test
    void shouldSkipAMessageThatCannotBeReadWithoutBlockingThePartition(CapturedOutput output) {
        when(offset.topicPartition()).thenReturn(PARTITION);
        when(offset.offset()).thenReturn(42L);

        StepVerifier.create(consumer().handle(received("{not json"))).expectNext(offset).verifyComplete();

        verify(offset).acknowledge();
        verifyNoInteractions(projectTriageEvent);
        assertThat(output).contains("Triage event skipped");
        assertThat(events(UrgencyEventsMetrics.SKIPPED)).isEqualTo(1.0);
    }

    @Test
    void shouldSkipAnEventOutsideTheContractOrOfAnUnknownType(CapturedOutput output) {
        when(offset.topicPartition()).thenReturn(PARTITION);

        StepVerifier.create(consumer().handle(received(
                        TriageEventParserTest.event("paciente_registrado", "GRAVE", "REGISTRADO", OCCURRED))))
                .expectNext(offset).verifyComplete();
        StepVerifier.create(consumer().handle(received(
                        TriageEventParserTest.event("paciente_trasladado", "CRITICO", "REGISTRADO", OCCURRED))))
                .expectNext(offset).verifyComplete();
        StepVerifier.create(consumer().handle(received(
                        TriageEventParserTest.event("paciente_alta", "CRITICO", "ALTA", "ayer"))))
                .expectNext(offset).verifyComplete();

        verifyNoInteractions(projectTriageEvent);
        assertThat(output).contains("Triage event skipped", "event type not projected");
        assertThat(events(UrgencyEventsMetrics.SKIPPED)).isEqualTo(3.0);
    }

    @Test
    void shouldNotCommitAnEventThatCouldNotBeStored() {
        when(projectTriageEvent.project(any())).thenReturn(Mono.error(
                new BusinessException(BusinessError.URGENCY_STATE_UNAVAILABLE, "")));

        StepVerifier.create(consumer().handle(received(
                        TriageEventParserTest.event("paciente_alta", "CRITICO", "ALTA", OCCURRED))))
                .expectError(BusinessException.class)
                .verify();

        verify(offset, never()).acknowledge();
    }

    @Test
    void shouldReadFromTheBeginningOnlyOnTheFirstAssignmentOfARebuild(CapturedOutput output) {
        when(projectTriageEvent.rebuildStarted()).thenReturn(Mono.empty());
        var partition = mock(ReceiverPartition.class);
        var options = consumer().options(true);

        options.assignListeners().forEach(listener -> listener.accept(List.of(partition)));
        options.assignListeners().forEach(listener -> listener.accept(List.of(partition)));

        verify(partition).seekToBeginning();
        verify(projectTriageEvent).rebuildStarted();
        assertThat(output).contains("rebuilding it from the beginning of the topic");
        assertThat(options.subscriptionTopics()).containsExactly("triaje.eventos");
        assertThat(options.consumerProperties()).containsEntry("group.id", "assistant")
                .containsEntry("bootstrap.servers", "localhost:9092").containsEntry("auto.offset.reset", "earliest");
        assertThat(options.consumerListener()).isInstanceOf(MicrometerConsumerListener.class);
    }

    @Test
    void shouldKeepTheCommittedPositionWhenTheProjectionExists() {
        var partition = mock(ReceiverPartition.class);

        consumer().options(false).assignListeners().forEach(listener -> listener.accept(List.of(partition)));

        verify(partition, never()).seekToBeginning();
        verifyNoInteractions(projectTriageEvent);
    }

    @Test
    void shouldLogWhenTheRebuildCannotBeMarked(CapturedOutput output) {
        when(projectTriageEvent.rebuildStarted()).thenReturn(Mono.error(new IllegalStateException("redis down")));

        consumer().options(true).assignListeners()
                .forEach(listener -> listener.accept(List.of(mock(ReceiverPartition.class))));

        assertThat(output).contains("Could not mark the projection as rebuilt");
    }

    @Test
    void shouldStartAndStopWithTheApplication() {
        var cancelled = new AtomicBoolean();
        when(projectTriageEvent.needsRebuild()).thenReturn(Mono.<Boolean>never().doOnCancel(() -> cancelled.set(true)));
        var consumer = consumer();

        assertThat(consumer.isRunning()).isFalse();
        consumer.start();
        assertThat(consumer.isRunning()).isTrue();
        consumer.stop();
        assertThat(consumer.isRunning()).isFalse();
        assertThat(cancelled).isTrue();
        consumer.stop();
    }

    @Test
    void shouldRequireWhereToReadFrom() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TriageEventsProperties(" ", "t", "g"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TriageEventsProperties("b", null, "g"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TriageEventsProperties("b", "t", ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new TriageEventsProperties(null, "t", "g"));
        assertThat(new TriageEventsProperties("b", "t", "g").groupId()).isEqualTo("g");
    }

    private TriageEventsConsumer consumer() {
        return new TriageEventsConsumer(projectTriageEvent, PROPERTIES, meters, observations);
    }

    private double events(String result) {
        return meters.get(UrgencyEventsMetrics.EVENTS).tag(UrgencyEventsMetrics.RESULT_TAG, result).counter().count();
    }

    private ReceiverRecord<String, String> received(String value) {
        return new ReceiverRecord<>(new ConsumerRecord<>(PARTITION.topic(), PARTITION.partition(), 42L,
                PATIENT.toString(), value), offset);
    }
}
