package com.vidacruz.triage.usecase.publishpendingevents;

import com.vidacruz.triage.model.outbox.OutboxMessage;
import com.vidacruz.triage.model.outbox.OutboxRelayPolicy;
import com.vidacruz.triage.model.outbox.gateways.EventPublisher;
import com.vidacruz.triage.model.outbox.gateways.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishPendingEventsUseCaseTest {

    private static final OutboxRelayPolicy POLICY = new OutboxRelayPolicy(50, Duration.ofSeconds(30));

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private EventPublisher eventPublisher;

    private PublishPendingEventsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishPendingEventsUseCase(outboxRepository, eventPublisher, POLICY);
    }

    @Test
    void shouldPublishEveryClaimedEventInOrderAndMarkItAfterTheBrokerAcknowledges() {
        var first = message("paciente_registrado");
        var second = message("paciente_en_consulta");
        when(outboxRepository.claimPending(50, Duration.ofSeconds(30))).thenReturn(Flux.just(first, second));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(outboxRepository.markPublished(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.publishPending()).expectNext(2L).verifyComplete();

        InOrder order = inOrder(eventPublisher, outboxRepository);
        order.verify(eventPublisher).publish(first);
        order.verify(outboxRepository).markPublished(first.eventId());
        order.verify(eventPublisher).publish(second);
        order.verify(outboxRepository).markPublished(second.eventId());
    }

    @Test
    void shouldStopTheRoundAtTheFirstFailureWithoutMarkingIt() {
        var first = message("paciente_registrado");
        var second = message("paciente_en_consulta");
        var brokerDown = new IllegalStateException("broker unavailable");
        when(outboxRepository.claimPending(50, Duration.ofSeconds(30))).thenReturn(Flux.just(first, second));
        when(eventPublisher.publish(first)).thenReturn(Mono.error(brokerDown));

        StepVerifier.create(useCase.publishPending()).expectErrorMatches(error -> error == brokerDown).verify();

        verify(eventPublisher, never()).publish(second);
        verify(outboxRepository, never()).markPublished(any());
    }

    @Test
    void shouldPublishNothingWhenTheOutboxIsEmpty() {
        when(outboxRepository.claimPending(50, Duration.ofSeconds(30))).thenReturn(Flux.empty());

        StepVerifier.create(useCase.publishPending()).expectNext(0L).verifyComplete();

        verifyNoInteractions(eventPublisher);
    }

    private static OutboxMessage message(String type) {
        return new OutboxMessage(UUID.randomUUID(), UUID.randomUUID().toString(), type, "{}", Instant.now());
    }
}
