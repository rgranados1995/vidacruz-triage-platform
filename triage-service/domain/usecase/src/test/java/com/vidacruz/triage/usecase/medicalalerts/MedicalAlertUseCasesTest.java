package com.vidacruz.triage.usecase.medicalalerts;

import com.vidacruz.triage.model.alert.AlertStatus;
import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.alert.gateways.MedicalAlertRepository;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.metrics.gateways.TriageMetrics;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalAlertUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:10:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private MedicalAlertRepository repository;
    @Mock
    private TriageMetrics metrics;

    @Test
    void shouldListTheActiveAlerts() {
        var alert = alert(1, AlertStatus.PENDING);
        when(repository.findActive()).thenReturn(Flux.just(alert));

        StepVerifier.create(new ListActiveAlertsUseCase(repository).listActive())
                .expectNext(alert)
                .verifyComplete();
    }

    @Test
    void shouldListenToTheChangesBeforeReadingTheActiveAlerts() {
        var changes = TestPublisher.<MedicalAlert>create();
        var active = alert(1, AlertStatus.PENDING);
        var changed = alert(2, AlertStatus.PENDING);
        when(repository.changes()).thenReturn(changes.flux());
        when(repository.findActive()).thenReturn(Flux.just(active));

        StepVerifier.create(new WatchAlertsUseCase(repository).watch())
                .expectNext(active)
                .then(() -> changes.next(changed))
                .expectNext(changed)
                .thenCancel()
                .verify();

        InOrder order = inOrder(repository);
        order.verify(repository).changes();
        order.verify(repository).findActive();
    }

    @Test
    void shouldAcknowledgeAPendingAlertAtTheCurrentTime() {
        var acknowledged = alert(1, AlertStatus.ACKNOWLEDGED);
        when(repository.acknowledge(1, "Dra. Rojas", NOW)).thenReturn(Mono.just(acknowledged));

        StepVerifier.create(acknowledgeUseCase().acknowledge(1, "Dra. Rojas"))
                .expectNext(acknowledged)
                .verifyComplete();

        verify(metrics).alertAcknowledged(TriageLevel.CRITICAL);
    }

    @Test
    void shouldExplainWhyAnAlertWasNotAcknowledged() {
        when(repository.acknowledge(1, "Dr. Gómez", NOW)).thenReturn(Mono.empty());
        when(repository.findById(1)).thenReturn(Mono.just(alert(1, AlertStatus.ACKNOWLEDGED)));

        StepVerifier.create(acknowledgeUseCase().acknowledge(1, "Dr. Gómez"))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getError() == BusinessError.ALERT_NOT_ACKNOWLEDGEABLE
                        && "la alerta ya fue reconocida por Dra. Rojas".equals(business.getDetail()))
                .verify();

        verifyNoInteractions(metrics);
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownAlert() {
        when(repository.acknowledge(9, "Dra. Rojas", NOW)).thenReturn(Mono.empty());
        when(repository.findById(9)).thenReturn(Mono.empty());

        StepVerifier.create(acknowledgeUseCase().acknowledge(9, "Dra. Rojas"))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getError() == BusinessError.ALERT_NOT_FOUND)
                .verify();
    }

    private AcknowledgeAlertUseCase acknowledgeUseCase() {
        return new AcknowledgeAlertUseCase(repository, CLOCK, metrics);
    }

    private static MedicalAlert alert(long id, AlertStatus status) {
        return MedicalAlert.builder()
                .id(id)
                .patientId(UUID.randomUUID())
                .triageLevel(TriageLevel.CRITICAL)
                .slaMinutes(5)
                .registeredAt(NOW.minusSeconds(600))
                .escalatedAt(NOW.minusSeconds(300))
                .status(status)
                .acknowledgedBy(status == AlertStatus.ACKNOWLEDGED ? "Dra. Rojas" : null)
                .updatedAt(NOW)
                .build();
    }
}
