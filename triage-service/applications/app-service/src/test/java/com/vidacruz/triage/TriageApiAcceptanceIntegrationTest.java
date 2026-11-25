package com.vidacruz.triage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance of RF-01 and RF-02 over HTTP against PostgreSQL: the reference patients E1 to E6, every transition of
 * the clinical flow and the error answers, with the same configuration as the deployment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestEnvironment.LOG_LEVEL)
@AutoConfigureWebTestClient
class TriageApiAcceptanceIntegrationTest {

    private static final String PATIENTS = "/triage/pacientes";
    private static final String KEY_HEADER = "X-API-Key";
    private static final String NORMAL_PATIENT = """
            {"nombreCompleto": "Paciente de prueba", "edad": 40, "frecuenciaCardiaca": 72, "presionSistolica": 120,
             "presionDiastolica": 80, "spo2": 99, "temperatura": 36.5, "dolor": 2, "gcs": 15,
             "quejaPrincipal": "Herida por cortar"}""";

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        TestEnvironment.register(registry);
    }

    @ParameterizedTest(name = "{0} -> {9} ({10} min)")
    @CsvSource(delimiter = '|', value = {
            "E1|130|180|110|97|36.8|8|15|Dolor torácico|CRITICO|5",
            "E2|95|150|95|91|37.0|4|15|Dificultad para respirar|URGENTE|10",
            "E3|88|130|85|98|38.2|6|15|Fiebre|URGENTE_2|30",
            "E4|72|120|80|99|36.5|2|15|Herida por cortar|NO_URGENTE|60",
            "E5|70|115|75|99|36.4|0|15|Renovación de recetario|NO_URGENTE_2|120",
            "E6|35|80|50|85|35.9|0|6|Inconsciente|CRITICO|5"})
    void shouldClassifyTheReferencePatientsOfTheStatement(ArgumentsAccessor row) {
        var body = """
                {"nombreCompleto": "Paciente %s", "edad": 50, "frecuenciaCardiaca": %s, "presionSistolica": %s,
                 "presionDiastolica": %s, "spo2": %s, "temperatura": %s, "dolor": %s, "gcs": %s,
                 "quejaPrincipal": "%s"}""".formatted(row.toArray());

        register(body).expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.nivel").isEqualTo(row.getString(9))
                .jsonPath("$.slaMinutos").isEqualTo(row.getInteger(10))
                .jsonPath("$.estado").isEqualTo("REGISTRADO")
                .jsonPath("$.fechaRegistro").exists();
    }

    @Test
    void shouldFollowTheWholeClinicalFlow() {
        var id = registeredId();

        changeStatus(id, "EN_CONSULTA").expectStatus().isOk()
                .expectBody().jsonPath("$.estado").isEqualTo("EN_CONSULTA");
        changeStatus(id, "REGISTRADO").expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-003");
        changeStatus(id, "ALTA").expectStatus().isOk();
        changeStatus(id, "EN_CONSULTA").expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("de \"alta\" a \"en consulta\"");

        client.get().uri(PATIENTS + "/" + id).header(KEY_HEADER, TestEnvironment.API_KEY).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ALTA")
                .jsonPath("$.nivel").isEqualTo("NO_URGENTE")
                .jsonPath("$.escalado").isEqualTo(Boolean.FALSE)
                .jsonPath("$.fechaInicioConsulta").exists()
                .jsonPath("$.tiempoEnEstadoActual").exists();
    }

    @Test
    void shouldDischargeARegisteredPatientDirectly() {
        var id = registeredId();

        changeStatus(id, "REGISTRADO").expectStatus().isEqualTo(409);
        changeStatus(id, "ALTA").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ALTA")
                .jsonPath("$.fechaInicioConsulta").isEmpty();
    }

    @Test
    void shouldReportEveryFieldOutOfRange() {
        register(NORMAL_PATIENT.replace("\"frecuenciaCardiaca\": 72", "\"frecuenciaCardiaca\": 300")
                .replace("\"spo2\": 99", "\"spo2\": 50"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-001")
                .jsonPath("$.errores.length()").isEqualTo(2)
                .jsonPath("$.errores[0].campo").isEqualTo("frecuenciaCardiaca")
                .jsonPath("$.errores[1].campo").isEqualTo("spo2");
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownPatient() {
        changeStatus(UUID.randomUUID().toString(), "ALTA").expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-002");
    }

    @Test
    void shouldProtectTheApiButNotTheProbesNorTheDocumentation() {
        client.post().uri(PATIENTS).contentType(MediaType.APPLICATION_JSON).bodyValue(NORMAL_PATIENT).exchange()
                .expectStatus().isUnauthorized();
        client.get().uri("/health/readiness").exchange().expectStatus().isOk();
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(docs -> assertThat(docs)
                        .contains("/triage/pacientes/{id}/estado", "registrarPaciente", "X-API-Key"));
        client.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
    }

    private String registeredId() {
        var created = register(NORMAL_PATIENT).expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
        return String.valueOf(Objects.requireNonNull(created).get("id"));
    }

    private WebTestClient.ResponseSpec register(String body) {
        return client.post().uri(PATIENTS).header(KEY_HEADER, TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private WebTestClient.ResponseSpec changeStatus(String id, String status) {
        return client.patch().uri(PATIENTS + "/" + id + "/estado").header(KEY_HEADER, TestEnvironment.API_KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"estado\": \"" + status + "\"}").exchange();
    }
}
