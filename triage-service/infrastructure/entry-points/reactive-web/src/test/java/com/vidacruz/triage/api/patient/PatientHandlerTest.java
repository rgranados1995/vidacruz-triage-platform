package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.api.patient.dto.ChangeStatusRequest;
import com.vidacruz.triage.api.patient.dto.RegisterPatientRequest;
import com.vidacruz.triage.usecase.changepatientstatus.ChangePatientStatusUseCase;
import com.vidacruz.triage.usecase.getpatient.GetPatientUseCase;
import com.vidacruz.triage.usecase.registerpatient.RegisterPatientUseCase;
import com.vidacruz.triage.usecase.getpatient.PatientDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Handler without a Spring context: checks what it answers and that it logs ids and codes but no personal data.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PatientHandlerTest {

    private static final UUID ID = UUID.fromString("3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12");
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    @Mock
    private RegisterPatientUseCase registerPatient;
    @Mock
    private ChangePatientStatusUseCase changePatientStatus;
    @Mock
    private GetPatientUseCase getPatient;

    @Test
    void shouldAnswerCreatedAndLogWithoutPersonalData(CapturedOutput output) {
        when(registerPatient.register(any())).thenReturn(Mono.just(patient(PatientStatus.REGISTERED)));
        var request = MockServerRequest.builder()
                .uri(URI.create("http://localhost/triage/pacientes"))
                .body(Mono.just(new RegisterPatientRequest("Ana María Pérez", 54, 130, 180, 110, 97,
                        new BigDecimal("36.8"), 8, 15, "Dolor torácico")));

        StepVerifier.create(handler().register(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.headers().getLocation()).hasToString("http://localhost/triage/pacientes/" + ID);
                })
                .verifyComplete();

        assertThat(output).contains("Patient registered")
                .doesNotContain("Ana María Pérez", "Dolor torácico");
    }

    @Test
    void shouldAnswerOkAndLogTheStatusChange(CapturedOutput output) {
        when(changePatientStatus.changeStatus(ID, PatientStatus.DISCHARGED))
                .thenReturn(Mono.just(new PatientDetail(patient(PatientStatus.DISCHARGED), Duration.ZERO)));
        var request = MockServerRequest.builder()
                .pathVariable(PatientHandler.ID_VARIABLE, ID.toString())
                .body(Mono.just(new ChangeStatusRequest("ALTA")));

        StepVerifier.create(handler().changeStatus(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        assertThat(output).contains("Patient status changed").doesNotContain("Ana María Pérez");
    }

    private PatientHandler handler() {
        return new PatientHandler(registerPatient, changePatientStatus, getPatient);
    }

    private static Patient patient(PatientStatus status) {
        return Patient.builder()
                .id(ID).fullName("Ana María Pérez").age(54).chiefComplaint("Dolor torácico")
                .vitalSigns(VitalSigns.builder().heartRate(130).systolicPressure(180).diastolicPressure(110)
                        .oxygenSaturation(97).temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15)
                        .build())
                .triageLevel(TriageLevel.CRITICAL).slaMinutes(5).status(status).escalated(false)
                .registeredAt(NOW).statusChangedAt(NOW)
                .build();
    }
}
