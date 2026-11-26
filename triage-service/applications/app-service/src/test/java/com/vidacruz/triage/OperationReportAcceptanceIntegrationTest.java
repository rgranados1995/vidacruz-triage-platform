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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-05 end to end: route, API key, real range policy and the SQL of the report over the real application. The
 * patients are stored on 1 June 2019, a day no other test uses, so the figures are exact.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
class OperationReportAcceptanceIntegrationTest {

    private static final String REPORT = "/triage/reportes/operacion";

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @Test
    void shouldReportTheOperationOfARangeByHour() {
        var critical = store(TriageLevel.CRITICAL, "10:05:00");
        store(TriageLevel.URGENT, "10:20:00");
        StepVerifier.create(patientRepository.update(
                        PatientChange.transition(critical, PatientStatus.IN_CONSULTATION, instantAt("10:20:00"))))
                .expectNextCount(1)
                .verifyComplete();

        // 05:00 in Bogotá (UTC-05:00) is 10:00 UTC; the + of an offset would have to travel as %2B
        get("desde=2019-06-01T05:00:00-05:00&hasta=2019-06-01T12:00:00Z").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.desde").isEqualTo("2019-06-01T10:00:00Z")
                .jsonPath("$.horas.length()").isEqualTo(2)
                .jsonPath("$.horas[0].hora").isEqualTo("2019-06-01T10:00:00Z")
                .jsonPath("$.horas[0].totalRegistrados").isEqualTo(2)
                .jsonPath("$.horas[0].porNivel.CRITICO").isEqualTo(1)
                .jsonPath("$.horas[0].porNivel.URGENTE").isEqualTo(1)
                .jsonPath("$.horas[0].porNivel.NO_URGENTE").isEqualTo(0)
                .jsonPath("$.horas[0].atendidos").isEqualTo(1)
                .jsonPath("$.horas[0].esperaPromedioMinutos").isEqualTo(15.0)
                .jsonPath("$.horas[0].esperaP95Minutos").isEqualTo(15.0)
                .jsonPath("$.horas[1].hora").isEqualTo("2019-06-01T11:00:00Z")
                .jsonPath("$.horas[1].totalRegistrados").isEqualTo(0)
                .jsonPath("$.horas[1].esperaPromedioMinutos").isEmpty()
                .jsonPath("$.resumen.totalRegistrados").isEqualTo(2)
                .jsonPath("$.resumen.atendidos").isEqualTo(1);
    }

    @Test
    void shouldRejectARangeLongerThanTheConfiguredMaximum() {
        get("desde=2019-01-01T00:00:00Z&hasta=2019-03-01T00:00:00Z").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-007")
                .jsonPath("$.detail").isEqualTo("el rango no puede superar 31 días");
    }

    @Test
    void shouldDocumentTheReportInTheOpenApi() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(docs -> assertThat(docs).contains("\"" + REPORT + "\"", "reporteOperacion", "ReporteOperacion"));
    }

    private WebTestClient.ResponseSpec get(String query) {
        return client.get().uri(REPORT + "?" + query).header("X-API-Key", TestEnvironment.API_KEY).exchange();
    }

    private Patient store(TriageLevel level, String time) {
        var registeredAt = instantAt(time);
        var patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Paciente del reporte")
                .age(35)
                .vitalSigns(VitalSigns.builder().heartRate(80).systolicPressure(120).diastolicPressure(80)
                        .oxygenSaturation(98).temperature(new BigDecimal("36.5")).painLevel(2).glasgowComaScale(15)
                        .build())
                .chiefComplaint("Control")
                .triageLevel(level)
                .slaMinutes(60)
                .status(PatientStatus.REGISTERED)
                // Already escalated: the SLA monitor of the test leaves these old patients alone
                .escalated(true)
                .registeredAt(registeredAt)
                .statusChangedAt(registeredAt)
                .build();
        StepVerifier.create(patientRepository.register(PatientChange.registration(patient)))
                .expectNextCount(1)
                .verifyComplete();
        return patient;
    }

    private static Instant instantAt(String time) {
        return Instant.parse("2019-06-01T" + time + "Z");
    }
}
