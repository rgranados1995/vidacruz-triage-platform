package com.vidacruz.triage.usecase.registerpatient;

import com.vidacruz.triage.model.audit.PatientEventType;
import com.vidacruz.triage.model.exception.InvalidPatientDataException;
import com.vidacruz.triage.model.metrics.gateways.TriageMetrics;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientIntake;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.model.triage.AdministrativeComplaints;
import com.vidacruz.triage.model.triage.SlaPolicy;
import com.vidacruz.triage.model.triage.TriageClassifier;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.model.validation.PatientDataValidator;
import com.vidacruz.triage.model.validation.PatientField;
import com.vidacruz.triage.model.validation.Violation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterPatientUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    /** Patient E1 of the statement, with spaces around the name to check that they are removed. */
    private static final PatientIntake CHEST_PAIN = PatientIntake.builder()
            .fullName("  Ana María Pérez ")
            .age(54)
            .chiefComplaint("Dolor torácico")
            .vitalSigns(VitalSigns.builder()
                    .heartRate(130).systolicPressure(180).diastolicPressure(110).oxygenSaturation(97)
                    .temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15)
                    .build())
            .build();

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private TriageMetrics metrics;

    private RegisterPatientUseCase useCase;

    @BeforeEach
    void setUp() {
        var sla = new SlaPolicy(Map.of(TriageLevel.CRITICAL, 5, TriageLevel.URGENT, 10, TriageLevel.URGENT_2, 30,
                TriageLevel.NON_URGENT, 60, TriageLevel.NON_URGENT_2, 120));
        var classifier = new TriageClassifier(new AdministrativeComplaints(List.of("renovación de recetario")));
        useCase = new RegisterPatientUseCase(patientRepository, new PatientDataValidator(), classifier, sla,
                Clock.fixed(NOW, ZoneOffset.UTC), metrics);
    }

    @Test
    void shouldRegisterAClassifiedPatientWithItsSlaAndTheRegistrationAudit() {
        when(patientRepository.register(any())).thenAnswer(call -> Mono.just(call.<PatientChange>getArgument(0)
                .patient()));

        StepVerifier.create(useCase.register(CHEST_PAIN))
                .assertNext(patient -> {
                    assertThat(patient.getId()).isNotNull();
                    assertThat(patient.getFullName()).isEqualTo("Ana María Pérez");
                    assertThat(patient.getTriageLevel()).isEqualTo(TriageLevel.CRITICAL);
                    assertThat(patient.getSlaMinutes()).isEqualTo(5);
                    assertThat(patient.getStatus()).isEqualTo(PatientStatus.REGISTERED);
                    assertThat(patient.isEscalated()).isFalse();
                    assertThat(patient.getRegisteredAt()).isEqualTo(NOW);
                    assertThat(patient.getStatusChangedAt()).isEqualTo(NOW);
                    assertThat(patient.getConsultationStartedAt()).isNull();
                })
                .verifyComplete();

        var stored = ArgumentCaptor.forClass(PatientChange.class);
        verify(patientRepository).register(stored.capture());
        assertThat(stored.getValue().isRegistration()).isTrue();
        assertThat(stored.getValue().auditEntry().eventType()).isEqualTo(PatientEventType.REGISTERED);
        assertThat(stored.getValue().auditEntry().newStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(stored.getValue().auditEntry().occurredAt()).isEqualTo(NOW);
        verify(metrics).patientClassified(TriageLevel.CRITICAL);
    }

    @Test
    void shouldGiveTheLongestSlaToAnAdministrativeComplaint() {
        var prescription = CHEST_PAIN.toBuilder()
                .chiefComplaint("Renovación de recetario")
                .vitalSigns(VitalSigns.builder()
                        .heartRate(70).systolicPressure(115).diastolicPressure(75).oxygenSaturation(99)
                        .temperature(new BigDecimal("36.4")).painLevel(0).glasgowComaScale(15)
                        .build())
                .build();
        when(patientRepository.register(any())).thenAnswer(call -> Mono.just(call.<PatientChange>getArgument(0)
                .patient()));

        StepVerifier.create(useCase.register(prescription))
                .assertNext(patient -> {
                    assertThat(patient.getTriageLevel()).isEqualTo(TriageLevel.NON_URGENT_2);
                    assertThat(patient.getSlaMinutes()).isEqualTo(120);
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectAnInvalidIntakeWithoutStoringIt() {
        var invalid = CHEST_PAIN.toBuilder().age(130).fullName("Al").build();

        StepVerifier.create(useCase.register(invalid))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(InvalidPatientDataException.class, invalidData ->
                                assertThat(invalidData.getViolations())
                                        .extracting(Violation::field)
                                        .containsExactly(PatientField.FULL_NAME, PatientField.AGE)))
                .verify();

        verifyNoInteractions(patientRepository, metrics);
    }

    @Test
    void shouldPropagateAStorageFailure() {
        var failure = new IllegalStateException("database unavailable");
        when(patientRepository.register(any())).thenReturn(Mono.error(failure));

        StepVerifier.create(useCase.register(CHEST_PAIN))
                .expectErrorMatches(error -> error == failure)
                .verify();

        verifyNoInteractions(metrics);
    }
}
