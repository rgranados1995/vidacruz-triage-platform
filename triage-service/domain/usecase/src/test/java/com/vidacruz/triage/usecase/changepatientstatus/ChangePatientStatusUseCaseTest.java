package com.vidacruz.triage.usecase.changepatientstatus;

import com.vidacruz.triage.model.audit.PatientEventType;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePatientStatusUseCaseTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant NOW = REGISTERED_AT.plusSeconds(240);
    private static final UUID PATIENT_ID = UUID.randomUUID();

    @Mock
    private PatientRepository patientRepository;

    private ChangePatientStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChangePatientStatusUseCase(patientRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldMoveTheStoredPatientAndAuditTheTransition() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Mono.just(patientIn(PatientStatus.REGISTERED)));
        when(patientRepository.update(any())).thenAnswer(call -> Mono.just(call.<PatientChange>getArgument(0)
                .patient()));

        StepVerifier.create(useCase.changeStatus(PATIENT_ID, PatientStatus.IN_CONSULTATION))
                .assertNext(patient -> {
                    assertThat(patient.getStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
                    assertThat(patient.getConsultationStartedAt()).isEqualTo(NOW);
                })
                .verifyComplete();

        var stored = ArgumentCaptor.forClass(PatientChange.class);
        verify(patientRepository).update(stored.capture());
        assertThat(stored.getValue().previousStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(stored.getValue().auditEntry().eventType()).isEqualTo(PatientEventType.CONSULTATION_STARTED);
        assertThat(stored.getValue().auditEntry().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void shouldFailWhenThePatientDoesNotExist() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.changeStatus(PATIENT_ID, PatientStatus.DISCHARGED))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(BusinessException.class, business ->
                                assertThat(business.getError()).isEqualTo(BusinessError.PATIENT_NOT_FOUND)))
                .verify();

        verify(patientRepository, never()).update(any());
    }

    @Test
    void shouldRejectATransitionOutsideTheClinicalFlowWithoutStoringIt() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Mono.just(patientIn(PatientStatus.DISCHARGED)));

        StepVerifier.create(useCase.changeStatus(PATIENT_ID, PatientStatus.IN_CONSULTATION))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(BusinessException.class, business ->
                                assertThat(business.getError()).isEqualTo(BusinessError.INVALID_STATUS_TRANSITION)))
                .verify();

        verify(patientRepository, never()).update(any());
    }

    @Test
    void shouldPropagateTheRejectionOfAConcurrentChange() {
        var concurrent = new BusinessException(BusinessError.INVALID_STATUS_TRANSITION, "changed concurrently");
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Mono.just(patientIn(PatientStatus.REGISTERED)));
        when(patientRepository.update(any())).thenReturn(Mono.error(concurrent));

        StepVerifier.create(useCase.changeStatus(PATIENT_ID, PatientStatus.DISCHARGED))
                .expectErrorMatches(error -> error == concurrent)
                .verify();
    }

    private static Patient patientIn(PatientStatus status) {
        return Patient.builder()
                .id(PATIENT_ID)
                .triageLevel(TriageLevel.URGENT)
                .slaMinutes(10)
                .status(status)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
    }
}
