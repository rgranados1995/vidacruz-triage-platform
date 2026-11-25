package com.vidacruz.triage;

import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientIntake;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.usecase.changepatientstatus.ChangePatientStatusUseCase;
import com.vidacruz.triage.usecase.getpatient.GetPatientUseCase;
import com.vidacruz.triage.usecase.registerpatient.RegisterPatientUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The use cases wired by Spring against PostgreSQL, with the SLA and the administrative complaints read from the
 * same variables as the deployment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = TestEnvironment.LOG_LEVEL)
class PatientFlowIntegrationTest {

    @Autowired
    private RegisterPatientUseCase registerPatient;
    @Autowired
    private ChangePatientStatusUseCase changePatientStatus;
    @Autowired
    private GetPatientUseCase getPatient;
    @Autowired
    private DatabaseClient databaseClient;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @Test
    void shouldRegisterAttendAndDischargeAPatientWithTheFullAudit() {
        var chestPain = intake("Dolor torácico", VitalSigns.builder().heartRate(130).systolicPressure(180)
                .diastolicPressure(110).oxygenSaturation(97).temperature(new BigDecimal("36.8")).painLevel(8)
                .glasgowComaScale(15).build());

        var registered = register(chestPain);
        assertThat(registered.getTriageLevel()).isEqualTo(TriageLevel.CRITICAL);
        assertThat(registered.getSlaMinutes()).isEqualTo(5);

        StepVerifier.create(changePatientStatus.changeStatus(registered.getId(), PatientStatus.IN_CONSULTATION)
                        .then(changePatientStatus.changeStatus(registered.getId(), PatientStatus.DISCHARGED))
                        .then(getPatient.getPatient(registered.getId())))
                .assertNext(detail -> {
                    assertThat(detail.patient().getStatus()).isEqualTo(PatientStatus.DISCHARGED);
                    assertThat(detail.patient().getConsultationStartedAt()).isNotNull();
                })
                .verifyComplete();

        StepVerifier.create(databaseClient.sql("""
                                SELECT event_type FROM clinical_audit WHERE patient_id = :id ORDER BY id""")
                        .bind("id", registered.getId())
                        .map(row -> row.get("event_type", String.class))
                        .all()
                        .collectList())
                .expectNext(List.of("REGISTERED", "CONSULTATION_STARTED", "DISCHARGED"))
                .verifyComplete();
    }

    @Test
    void shouldReadTheAdministrativeComplaintsWithAccentsFromTheEnvironment() {
        var prescription = intake("Renovación de recetario", VitalSigns.builder().heartRate(70).systolicPressure(115)
                .diastolicPressure(75).oxygenSaturation(99).temperature(new BigDecimal("36.4")).painLevel(0)
                .glasgowComaScale(15).build());

        var registered = register(prescription);

        assertThat(registered.getTriageLevel()).isEqualTo(TriageLevel.NON_URGENT_2);
        assertThat(registered.getSlaMinutes()).isEqualTo(120);
    }

    @Test
    void shouldReportAnUnknownPatient() {
        StepVerifier.create(getPatient.getPatient(UUID.randomUUID()))
                .expectErrorMessage("TRG-002 No existe un paciente con el id indicado")
                .verify();
    }

    private Patient register(PatientIntake intake) {
        var registered = new Patient[1];
        StepVerifier.create(registerPatient.register(intake))
                .consumeNextWith(patient -> registered[0] = patient)
                .verifyComplete();
        return registered[0];
    }

    private static PatientIntake intake(String complaint, VitalSigns signs) {
        return PatientIntake.builder().fullName("Paciente de prueba").age(40).chiefComplaint(complaint)
                .vitalSigns(signs).build();
    }
}
