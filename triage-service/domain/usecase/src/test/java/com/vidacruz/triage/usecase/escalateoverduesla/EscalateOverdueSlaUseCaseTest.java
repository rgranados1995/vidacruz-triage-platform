package com.vidacruz.triage.usecase.escalateoverduesla;

import com.vidacruz.triage.model.audit.PatientEventType;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.model.triage.SlaMonitorPolicy;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalateOverdueSlaUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @Mock
    private PatientRepository patientRepository;

    private EscalateOverdueSlaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EscalateOverdueSlaUseCase(patientRepository, new SlaMonitorPolicy(20),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldEscalateEveryOverduePatientAtTheTimeOfTheRound() {
        var first = waitingSince(Duration.ofMinutes(30));
        var second = waitingSince(Duration.ofMinutes(10));
        when(patientRepository.findOverdue(NOW, 20)).thenReturn(Flux.just(first, second));
        when(patientRepository.escalate(any())).thenAnswer(call -> Mono.just(call.<PatientChange>getArgument(0)
                .patient()));

        StepVerifier.create(useCase.escalateOverdue()).expectNext(2L).verifyComplete();

        var escalations = ArgumentCaptor.forClass(PatientChange.class);
        verify(patientRepository, times(2)).escalate(escalations.capture());
        assertThat(escalations.getAllValues()).extracting(change -> change.patient().getId())
                .containsExactly(first.getId(), second.getId());
        assertThat(escalations.getAllValues()).allSatisfy(change -> {
            assertThat(change.patient().isEscalated()).isTrue();
            assertThat(change.auditEntry().eventType()).isEqualTo(PatientEventType.ESCALATED);
            assertThat(change.auditEntry().occurredAt()).isEqualTo(NOW);
        });
    }

    @Test
    void shouldCountOnlyTheEscalationsThatWereApplied() {
        when(patientRepository.findOverdue(NOW, 20))
                .thenReturn(Flux.just(waitingSince(Duration.ofMinutes(30)), waitingSince(Duration.ofMinutes(20))));
        when(patientRepository.escalate(any()))
                .thenReturn(Mono.empty())
                .thenAnswer(call -> Mono.just(call.<PatientChange>getArgument(0).patient()));

        StepVerifier.create(useCase.escalateOverdue()).expectNext(1L).verifyComplete();
    }

    @Test
    void shouldSkipAPatientWhoseSlaHasNotPassed() {
        when(patientRepository.findOverdue(NOW, 20)).thenReturn(Flux.just(waitingSince(Duration.ofMinutes(2))));

        StepVerifier.create(useCase.escalateOverdue()).expectNext(0L).verifyComplete();

        verify(patientRepository, never()).escalate(any());
    }

    @Test
    void shouldPropagateAStorageFailure() {
        var failure = new IllegalStateException("database unavailable");
        when(patientRepository.findOverdue(NOW, 20)).thenReturn(Flux.error(failure));

        StepVerifier.create(useCase.escalateOverdue()).expectErrorMatches(error -> error == failure).verify();
    }

    private static Patient waitingSince(Duration waiting) {
        var registeredAt = NOW.minus(waiting);
        return Patient.builder()
                .id(UUID.randomUUID())
                .triageLevel(TriageLevel.CRITICAL)
                .slaMinutes(5)
                .status(PatientStatus.REGISTERED)
                .escalated(false)
                .registeredAt(registeredAt)
                .statusChangedAt(registeredAt)
                .build();
    }
}
