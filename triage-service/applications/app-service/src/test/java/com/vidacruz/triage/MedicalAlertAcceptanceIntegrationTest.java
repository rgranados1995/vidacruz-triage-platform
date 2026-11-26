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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-06 end to end with the real SLA monitor: the alert of an overdue patient reaches a client of the stream, which
 * then follows it while a doctor acknowledges it and the patient enters the consultation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
class MedicalAlertAcceptanceIntegrationTest {

    private static final String ALERTS = "/triage/alertas";
    private static final String KEY_HEADER = "X-API-Key";
    private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> EVENT =
            new ParameterizedTypeReference<>() { };

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @Test
    void shouldStreamTheAlertOfAnOverduePatientUntilItIsResolved() {
        var patient = overduePatient();
        var alertId = new AtomicLong();
        var stream = client.mutate().responseTimeout(Duration.ofSeconds(30)).build()
                .get().uri(ALERTS + "/stream").header(KEY_HEADER, TestEnvironment.API_KEY)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(EVENT)
                .getResponseBody()
                .filter(event -> "alerta".equals(event.event()))
                .map(ServerSentEvent::data)
                .filter(alert -> patient.getId().toString().equals(alert.get("pacienteId")));

        StepVerifier.create(stream)
                .then(() -> store(patient))
                .assertNext(alert -> {
                    assertThat(alert).containsEntry("estado", "PENDIENTE").containsEntry("nivel", "CRITICO")
                            .containsEntry("slaMinutos", 5).containsEntry("minutosEsperaAlEscalar", 10);
                    alertId.set(((Number) alert.get("id")).longValue());
                })
                .then(() -> {
                    assertActive(alertId.get());
                    acknowledge(alertId.get());
                })
                .assertNext(alert -> assertThat(alert).containsEntry("estado", "RECONOCIDA")
                        .containsEntry("reconocidaPor", "Dra. Rojas"))
                .then(() -> startConsultation(patient.getId()))
                .assertNext(alert -> {
                    assertThat(alert).containsEntry("estado", "RESUELTA");
                    assertThat(alert.get("fechaResolucion")).isNotNull();
                })
                .thenCancel()
                .verify(Duration.ofSeconds(30));

        client.post().uri(ALERTS + "/" + alertId.get() + "/reconocimiento").header(KEY_HEADER, TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"responsable\": \"Dr. Gómez\"}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-009");
    }

    @Test
    void shouldSendHeartbeatsWhileNothingChanges() {
        var stream = client.get().uri(ALERTS + "/stream").header(KEY_HEADER, TestEnvironment.API_KEY)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(EVENT)
                .getResponseBody()
                .filter(event -> "latido".equals(event.comment()));

        StepVerifier.create(stream.take(2)).expectNextCount(2).verifyComplete();
    }

    private void assertActive(long alertId) {
        client.get().uri(ALERTS).header(KEY_HEADER, TestEnvironment.API_KEY).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.id == " + alertId + ")].estado").isEqualTo("PENDIENTE");
    }

    private void acknowledge(long alertId) {
        client.post().uri(ALERTS + "/" + alertId + "/reconocimiento").header(KEY_HEADER, TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"responsable\": \"Dra. Rojas\"}")
                .exchange()
                .expectStatus().isOk();
    }

    private void startConsultation(UUID patientId) {
        client.patch().uri("/triage/pacientes/" + patientId + "/estado").header(KEY_HEADER, TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"estado\": \"EN_CONSULTA\"}")
                .exchange()
                .expectStatus().isOk();
    }

    private void store(Patient patient) {
        StepVerifier.create(patientRepository.register(PatientChange.registration(patient)))
                .expectNextCount(1)
                .verifyComplete();
    }

    /** A critical patient (SLA of 5 minutes) registered ten minutes ago: the monitor escalates it on its next round. */
    private static Patient overduePatient() {
        var registeredAt = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MICROS);
        return Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Paciente de alertas")
                .age(70)
                .vitalSigns(VitalSigns.builder().heartRate(130).systolicPressure(180).diastolicPressure(110)
                        .oxygenSaturation(97).temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15)
                        .build())
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICAL)
                .slaMinutes(5)
                .status(PatientStatus.REGISTERED)
                .escalated(false)
                .registeredAt(registeredAt)
                .statusChangedAt(registeredAt)
                .build();
    }
}
