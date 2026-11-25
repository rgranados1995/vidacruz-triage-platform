package com.vidacruz.triage.usecase.getpatient;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPatientUseCaseTest {

    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final UUID PATIENT_ID = UUID.randomUUID();

    @Mock
    private PatientRepository patientRepository;

    private GetPatientUseCase useCase;

    @BeforeEach
    void setUp() {
        var now = STATUS_CHANGED_AT.plus(Duration.ofMinutes(12));
        useCase = new GetPatientUseCase(patientRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void shouldReturnThePatientWithTheTimeInItsCurrentStatus() {
        var patient = Patient.builder()
                .id(PATIENT_ID)
                .status(PatientStatus.IN_CONSULTATION)
                .statusChangedAt(STATUS_CHANGED_AT)
                .build();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Mono.just(patient));

        StepVerifier.create(useCase.getPatient(PATIENT_ID))
                .assertNext(detail -> {
                    assertThat(detail.patient()).isSameAs(patient);
                    assertThat(detail.timeInCurrentStatus()).isEqualTo(Duration.ofMinutes(12));
                })
                .verifyComplete();
    }

    @Test
    void shouldFailWhenThePatientDoesNotExist() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.getPatient(PATIENT_ID))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(BusinessException.class, business ->
                                assertThat(business.getError()).isEqualTo(BusinessError.PATIENT_NOT_FOUND)))
                .verify();
    }
}
