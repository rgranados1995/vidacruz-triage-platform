package com.vidacruz.triage;

import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-03 and RN-03 end to end with the real SLA monitor (one round every 300 ms in the tests): a patient whose SLA
 * passed is escalated once, audited and published; a patient already in consultation is never escalated.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
class SlaEscalationAcceptanceIntegrationTest {

    private static final String COUNT_ESCALATIONS = """
            SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :id AND event_type = 'ESCALATED'""";

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private DatabaseClient databaseClient;
    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @Test
    void shouldEscalateOnceAPatientWhoseSlaPassedAndPublishIt() throws Exception {
        var waiting = store(criticalRegisteredMinutesAgo(10, PatientStatus.REGISTERED));

        StepVerifier.create(escalatedFlag(waiting.getId())
                        .filter(Boolean.TRUE::equals)
                        .repeatWhenEmpty(rounds -> rounds.delayElements(Duration.ofMillis(200)).take(50)))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
        // Several more monitor rounds run meanwhile: none may escalate the patient again (RN-03)
        StepVerifier.create(Mono.delay(Duration.ofMillis(1500))).expectNextCount(1).verifyComplete();

        client.get().uri("/triage/pacientes/" + waiting.getId()).header("X-API-Key", TestEnvironment.API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.escalado").isEqualTo(Boolean.TRUE)
                .jsonPath("$.estado").isEqualTo("REGISTRADO");
        StepVerifier.create(escalationsAudited(waiting.getId())).expectNext(1L).verifyComplete();
        var events = TriageTopicReader.eventsOf(waiting.getId().toString(), 3, Duration.ofSeconds(8));
        assertThat(events).extracting(event -> event.get("type").asText())
                .containsExactly("paciente_registrado", "paciente_escalado");
        assertThat(events.getLast().get("data").get("escalado").asBoolean()).isTrue();
        assertThat(events.getLast().get("data").get("nivel").asText()).isEqualTo("CRITICO");
    }

    @Test
    void shouldNeverEscalateAPatientAlreadyInConsultation() {
        var attended = store(criticalRegisteredMinutesAgo(10, PatientStatus.IN_CONSULTATION));

        StepVerifier.create(Mono.delay(Duration.ofMillis(1500))).expectNextCount(1).verifyComplete();

        StepVerifier.create(escalatedFlag(attended.getId())).expectNext(Boolean.FALSE).verifyComplete();
        StepVerifier.create(escalationsAudited(attended.getId())).expectNext(0L).verifyComplete();
    }

    private Patient store(Patient patient) {
        StepVerifier.create(patientRepository.register(PatientChange.registration(patient)))
                .expectNextCount(1)
                .verifyComplete();
        return patient;
    }

    private Mono<Boolean> escalatedFlag(UUID patientId) {
        return databaseClient.sql("SELECT escalated FROM patient WHERE id = :id")
                .bind("id", patientId)
                .map(row -> row.get("escalated", Boolean.class))
                .one();
    }

    private Mono<Long> escalationsAudited(UUID patientId) {
        return databaseClient.sql(COUNT_ESCALATIONS)
                .bind("id", patientId)
                .map(row -> row.get("total", Long.class))
                .one();
    }

    /** A critical patient (SLA of 5 minutes) registered ten minutes ago, stored directly to skip the wait. */
    private static Patient criticalRegisteredMinutesAgo(long minutes, PatientStatus status) {
        var registeredAt = Instant.now().minus(Duration.ofMinutes(minutes)).truncatedTo(ChronoUnit.MICROS);
        return Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Paciente en espera")
                .age(60)
                .vitalSigns(VitalSigns.builder().heartRate(130).systolicPressure(180).diastolicPressure(110)
                        .oxygenSaturation(97).temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15)
                        .build())
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICAL)
                .slaMinutes(5)
                .status(status)
                .escalated(false)
                .registeredAt(registeredAt)
                .statusChangedAt(registeredAt)
                .build();
    }
}
