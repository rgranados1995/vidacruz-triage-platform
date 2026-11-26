package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.config.SecurityHeadersConfig;
import com.vidacruz.triage.api.error.ApiErrorHandler;
import com.vidacruz.triage.api.error.ApiErrorWriter;
import com.vidacruz.triage.api.security.ApiKeySecurityConfig;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.exception.InvalidPatientDataException;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientIntake;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.model.validation.PatientField;
import com.vidacruz.triage.model.validation.Violation;
import com.vidacruz.triage.usecase.changepatientstatus.ChangePatientStatusUseCase;
import com.vidacruz.triage.usecase.getpatient.GetPatientUseCase;
import com.vidacruz.triage.usecase.getpatient.PatientDetail;
import com.vidacruz.triage.usecase.registerpatient.RegisterPatientUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest
@ContextConfiguration(classes = {PatientRouter.class, PatientHandler.class, ApiErrorHandler.class,
        ApiErrorWriter.class, ApiKeySecurityConfig.class, SecurityHeadersConfig.class})
@TestPropertySource(properties = {"routes.patients=/triage/pacientes", "security.api-key=" + PatientRouterTest.KEY})
class PatientRouterTest {

    static final String KEY = "test-api-key-0001";
    private static final String PATIENTS = "/triage/pacientes";
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final UUID ID = UUID.fromString("3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12");
    private static final String E1_BODY = """
            {"nombreCompleto": "Ana María Pérez", "edad": 54, "frecuenciaCardiaca": 130, "presionSistolica": 180,
             "presionDiastolica": 110, "spo2": 97, "temperatura": 36.8, "dolor": 8, "gcs": 15,
             "quejaPrincipal": "Dolor torácico"}""";

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private RegisterPatientUseCase registerPatient;
    @MockitoBean
    private ChangePatientStatusUseCase changePatientStatus;
    @MockitoBean
    private GetPatientUseCase getPatient;

    @Test
    void shouldRegisterAPatientAndPointToItsDetail() {
        when(registerPatient.register(any())).thenReturn(Mono.just(patient(PatientStatus.REGISTERED)));

        post(E1_BODY).expectStatus().isCreated()
                .expectHeader().location(PATIENTS + "/" + ID)
                .expectBody()
                .jsonPath("$.id").isEqualTo(ID.toString())
                .jsonPath("$.nivel").isEqualTo("CRITICO")
                .jsonPath("$.slaMinutos").isEqualTo(5)
                .jsonPath("$.estado").isEqualTo("REGISTRADO")
                .jsonPath("$.fechaRegistro").isEqualTo("2026-11-25T10:00:00Z");

        var intake = ArgumentCaptor.forClass(PatientIntake.class);
        verify(registerPatient).register(intake.capture());
        assertThat(intake.getValue().fullName()).isEqualTo("Ana María Pérez");
        assertThat(intake.getValue().age()).isEqualTo(54);
        assertThat(intake.getValue().chiefComplaint()).isEqualTo("Dolor torácico");
        assertThat(intake.getValue().vitalSigns()).isEqualTo(e1Signs());
    }

    @Test
    void shouldReportEveryInvalidFieldWithItsContractName() {
        when(registerPatient.register(any())).thenReturn(Mono.error(new InvalidPatientDataException(List.of(
                new Violation(PatientField.HEART_RATE, "La frecuencia cardíaca debe estar entre 20 y 250 lpm"),
                new Violation(PatientField.FULL_NAME, "El nombre completo debe tener entre 3 y 100 caracteres")))));

        post(E1_BODY).expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.codigo").isEqualTo("TRG-001")
                .jsonPath("$.title").isEqualTo("Los datos del paciente no son válidos")
                .jsonPath("$.instance").isEqualTo(PATIENTS)
                .jsonPath("$.errores[0].campo").isEqualTo("frecuenciaCardiaca")
                .jsonPath("$.errores[0].mensaje").isEqualTo("La frecuencia cardíaca debe estar entre 20 y 250 lpm")
                .jsonPath("$.errores[1].campo").isEqualTo("nombreCompleto");
    }

    @Test
    void shouldRejectABodyThatIsNotJson() {
        post("{nombreCompleto: ").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-004")
                .jsonPath("$.detail").isEqualTo("El cuerpo debe ser un JSON válido")
                .jsonPath("$.errores").doesNotExist();
        verifyNoInteractions(registerPatient);
    }

    @Test
    void shouldNameTheFieldThatHasTheWrongType() {
        post("{\"edad\": \"cincuenta\"}").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-004")
                .jsonPath("$.errores[0].campo").isEqualTo("edad")
                .jsonPath("$.errores[0].mensaje").isEqualTo("El campo edad tiene un tipo o formato inválido");
        verifyNoInteractions(registerPatient);
    }

    @Test
    void shouldRequireABody() {
        client.post().uri(PATIENTS).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").isEqualTo("El cuerpo de la solicitud es obligatorio");
    }

    @Test
    void shouldAnswerUnsupportedMediaTypeForABodyThatIsNotJson() {
        client.post().uri(PATIENTS).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY)
                .contentType(MediaType.TEXT_PLAIN).bodyValue("paciente")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-006");
    }

    @Test
    void shouldChangeTheStatusAndReturnTheDetail() {
        when(changePatientStatus.changeStatus(ID, PatientStatus.IN_CONSULTATION)).thenReturn(Mono.just(
                new PatientDetail(patient(PatientStatus.IN_CONSULTATION), Duration.ofMillis(4))));

        patch(ID.toString(), "{\"estado\": \"EN_CONSULTA\"}").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("EN_CONSULTA")
                .jsonPath("$.tiempoEnEstadoActual").isEqualTo("PT0.004S");
    }

    @Test
    void shouldAnswerConflictForATransitionOutsideTheClinicalFlow() {
        when(changePatientStatus.changeStatus(ID, PatientStatus.IN_CONSULTATION)).thenReturn(Mono.error(
                new BusinessException(BusinessError.INVALID_STATUS_TRANSITION, "de \"alta\" a \"en consulta\"")));

        patch(ID.toString(), "{\"estado\": \"EN_CONSULTA\"}").expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-003")
                .jsonPath("$.title").isEqualTo("Transición de estado no permitida")
                .jsonPath("$.detail").isEqualTo("de \"alta\" a \"en consulta\"");
    }

    @Test
    void shouldRejectAStatusOutsideTheContract() {
        patch(ID.toString(), "{\"estado\": \"OBSERVACION\"}").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errores[0].campo").isEqualTo("estado")
                .jsonPath("$.errores[0].mensaje").isEqualTo("El estado debe ser REGISTRADO, EN_CONSULTA o ALTA");
        verify(changePatientStatus, never()).changeStatus(any(), any());
    }

    @Test
    void shouldRejectAnIdThatIsNotAUuid() {
        get("12345").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errores[0].campo").isEqualTo("id")
                .jsonPath("$.errores[0].mensaje").isEqualTo("El id del paciente debe ser un UUID");
        verifyNoInteractions(getPatient);
    }

    @Test
    void shouldReturnTheFullDetailOfAPatient() {
        when(getPatient.getPatient(ID))
                .thenReturn(Mono.just(new PatientDetail(patient(PatientStatus.REGISTERED), Duration.ofMinutes(12))));

        get(ID.toString()).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nombreCompleto").isEqualTo("Ana María Pérez")
                .jsonPath("$.edad").isEqualTo(54)
                .jsonPath("$.frecuenciaCardiaca").isEqualTo(130)
                .jsonPath("$.presionSistolica").isEqualTo(180)
                .jsonPath("$.presionDiastolica").isEqualTo(110)
                .jsonPath("$.spo2").isEqualTo(97)
                .jsonPath("$.temperatura").isEqualTo(36.8)
                .jsonPath("$.dolor").isEqualTo(8)
                .jsonPath("$.gcs").isEqualTo(15)
                .jsonPath("$.quejaPrincipal").isEqualTo("Dolor torácico")
                .jsonPath("$.nivel").isEqualTo("CRITICO")
                .jsonPath("$.estado").isEqualTo("REGISTRADO")
                .jsonPath("$.escalado").isEqualTo(Boolean.FALSE)
                .jsonPath("$.fechaCambioEstado").isEqualTo("2026-11-25T10:00:00Z")
                .jsonPath("$.fechaInicioConsulta").isEmpty()
                .jsonPath("$.tiempoEnEstadoActual").isEqualTo("PT12M");
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownPatient() {
        when(getPatient.getPatient(ID)).thenReturn(Mono.error(new BusinessException(BusinessError.PATIENT_NOT_FOUND)));

        get(ID.toString()).expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-002")
                .jsonPath("$.detail").doesNotExist();
    }

    @Test
    void shouldHideTheDetailOfAnUnexpectedError() {
        when(getPatient.getPatient(ID)).thenReturn(Mono.error(new IllegalStateException("db 10.0.0.7 refused")));

        get(ID.toString()).expectStatus().is5xxServerError()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("TRG-999").doesNotContain("10.0.0.7"));
    }

    @Test
    void shouldRequireAValidApiKey() {
        client.get().uri(PATIENTS + "/" + ID).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-005");
        client.get().uri(PATIENTS + "/" + ID).header(ApiKeySecurityConfig.API_KEY_HEADER, "wrong-api-key-0001")
                .exchange()
                .expectStatus().isUnauthorized();
        verifyNoInteractions(getPatient);
    }

    @Test
    void shouldAnswerUnsupportedRoutesAndMethods() {
        client.get().uri("/triage/otra-ruta").header(ApiKeySecurityConfig.API_KEY_HEADER, KEY).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-006");
        // Router functions answer 404 for a method without a route (they do not compute the allowed methods)
        client.delete().uri(PATIENTS + "/" + ID).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-006");
    }

    @Test
    void shouldAddTheSecurityHeaders() {
        when(getPatient.getPatient(ID))
                .thenReturn(Mono.just(new PatientDetail(patient(PatientStatus.REGISTERED), Duration.ZERO)));

        get(ID.toString()).expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().exists("Content-Security-Policy");
    }

    private WebTestClient.ResponseSpec post(String body) {
        return client.post().uri(PATIENTS).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private WebTestClient.ResponseSpec patch(String id, String body) {
        return client.patch().uri(PATIENTS + "/" + id + "/estado").header(ApiKeySecurityConfig.API_KEY_HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private WebTestClient.ResponseSpec get(String id) {
        return client.get().uri(PATIENTS + "/" + id).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY).exchange();
    }

    private static VitalSigns e1Signs() {
        return VitalSigns.builder().heartRate(130).systolicPressure(180).diastolicPressure(110).oxygenSaturation(97)
                .temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15).build();
    }

    private static Patient patient(PatientStatus status) {
        return Patient.builder()
                .id(ID).fullName("Ana María Pérez").age(54).vitalSigns(e1Signs()).chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICAL).slaMinutes(5).status(status).escalated(false)
                .registeredAt(REGISTERED_AT).statusChangedAt(REGISTERED_AT)
                .build();
    }
}
