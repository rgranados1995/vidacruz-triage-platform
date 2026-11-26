package com.vidacruz.triage.api.alert;

import com.vidacruz.triage.api.config.SecurityHeadersConfig;
import com.vidacruz.triage.api.error.ApiErrorHandler;
import com.vidacruz.triage.api.error.ApiErrorWriter;
import com.vidacruz.triage.api.security.ApiKeySecurityConfig;
import com.vidacruz.triage.model.alert.AlertStatus;
import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.usecase.medicalalerts.AcknowledgeAlertUseCase;
import com.vidacruz.triage.usecase.medicalalerts.ListActiveAlertsUseCase;
import com.vidacruz.triage.usecase.medicalalerts.WatchAlertsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest
@ContextConfiguration(classes = {AlertRouter.class, AlertHandler.class, ApiErrorHandler.class, ApiErrorWriter.class,
        ApiKeySecurityConfig.class, SecurityHeadersConfig.class})
@TestPropertySource(properties = {"routes.alerts=/triage/alertas", "alerts.stream.heartbeat=1h",
        "security.api-key=" + AlertRouterTest.KEY})
class AlertRouterTest {

    static final String KEY = "test-api-key-0001";
    private static final String ALERTS = "/triage/alertas";
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final UUID PATIENT = UUID.fromString("3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12");

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ListActiveAlertsUseCase listActiveAlerts;
    @MockitoBean
    private WatchAlertsUseCase watchAlerts;
    @MockitoBean
    private AcknowledgeAlertUseCase acknowledgeAlert;

    @Test
    void shouldListTheActiveAlertsWithTheContractNames() {
        when(listActiveAlerts.listActive()).thenReturn(Flux.just(alert(42, AlertStatus.PENDING)));

        client.get().uri(ALERTS).header(ApiKeySecurityConfig.API_KEY_HEADER, KEY).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(42)
                .jsonPath("$[0].pacienteId").isEqualTo(PATIENT.toString())
                .jsonPath("$[0].nivel").isEqualTo("CRITICO")
                .jsonPath("$[0].slaMinutos").isEqualTo(5)
                .jsonPath("$[0].fechaRegistro").isEqualTo("2026-11-25T10:00:00Z")
                .jsonPath("$[0].fechaEscalamiento").isEqualTo("2026-11-25T10:05:30Z")
                .jsonPath("$[0].minutosEsperaAlEscalar").isEqualTo(5)
                .jsonPath("$[0].estado").isEqualTo("PENDIENTE")
                .jsonPath("$[0].reconocidaPor").isEmpty()
                .jsonPath("$[0].fechaResolucion").isEmpty()
                .jsonPath("$[0].actualizadaEn").isEqualTo("2026-11-25T10:05:30Z");
    }

    @Test
    void shouldStreamTheActiveAlertsAndThenTheirChanges() {
        Sinks.Many<MedicalAlert> changes = Sinks.many().unicast().onBackpressureBuffer();
        when(watchAlerts.watch()).thenReturn(Flux.concat(Flux.just(alert(42, AlertStatus.PENDING)), changes.asFlux()));

        var events = client.get().uri(ALERTS + "/stream").header(ApiKeySecurityConfig.API_KEY_HEADER, KEY)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() { })
                .getResponseBody();

        StepVerifier.create(events)
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("alerta");
                    assertThat(event.data()).contains("\"id\":42", "\"estado\":\"PENDIENTE\"");
                })
                .then(() -> changes.tryEmitNext(alert(42, AlertStatus.ACKNOWLEDGED)))
                .assertNext(event -> assertThat(event.data()).contains("\"estado\":\"RECONOCIDA\"",
                        "\"reconocidaPor\":\"Dra. Rojas\""))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldAcknowledgeAnAlert() {
        when(acknowledgeAlert.acknowledge(42, "Dra. Rojas")).thenReturn(Mono.just(alert(42, AlertStatus.ACKNOWLEDGED)));

        acknowledge("42", "{\"responsable\": \"  Dra. Rojas \"}").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("RECONOCIDA")
                .jsonPath("$.reconocidaPor").isEqualTo("Dra. Rojas");
    }

    @Test
    void shouldAnswerConflictForAnAlertAlreadyTaken() {
        when(acknowledgeAlert.acknowledge(42, "Dr. Gómez")).thenReturn(Mono.error(new BusinessException(
                BusinessError.ALERT_NOT_ACKNOWLEDGEABLE, "la alerta ya fue reconocida por Dra. Rojas")));

        acknowledge("42", "{\"responsable\": \"Dr. Gómez\"}").expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-009")
                .jsonPath("$.title").isEqualTo("La alerta no se puede reconocer")
                .jsonPath("$.detail").isEqualTo("la alerta ya fue reconocida por Dra. Rojas");
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownAlert() {
        when(acknowledgeAlert.acknowledge(7, "Dra. Rojas"))
                .thenReturn(Mono.error(new BusinessException(BusinessError.ALERT_NOT_FOUND)));

        acknowledge("7", "{\"responsable\": \"Dra. Rojas\"}").expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-008");
    }

    @Test
    void shouldRejectAnInvalidIdOrResponsible() {
        acknowledge("abc", "{\"responsable\": \"Dra. Rojas\"}").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-004")
                .jsonPath("$.errores[0].campo").isEqualTo("id")
                .jsonPath("$.errores[0].mensaje").isEqualTo("El id de la alerta debe ser un número");
        acknowledge("42", "{\"responsable\": \"  \"}").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errores[0].campo").isEqualTo("responsable")
                .jsonPath("$.errores[0].mensaje").isEqualTo("El responsable debe tener entre 3 y 100 caracteres");
        acknowledge("42", "{}").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errores[0].campo").isEqualTo("responsable");
        verify(acknowledgeAlert, never()).acknowledge(anyLong(), anyString());
    }

    @Test
    void shouldRequireAValidApiKey() {
        client.get().uri(ALERTS + "/stream").accept(MediaType.TEXT_EVENT_STREAM).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-005");
        client.get().uri(ALERTS).exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(watchAlerts, listActiveAlerts);
    }

    private WebTestClient.ResponseSpec acknowledge(String id, String body) {
        return client.post().uri(ALERTS + "/" + id + "/reconocimiento").header(ApiKeySecurityConfig.API_KEY_HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private static MedicalAlert alert(long id, AlertStatus status) {
        var escalatedAt = REGISTERED_AT.plusSeconds(330);
        var acknowledged = status == AlertStatus.ACKNOWLEDGED;
        return MedicalAlert.builder()
                .id(id)
                .patientId(PATIENT)
                .triageLevel(TriageLevel.CRITICAL)
                .slaMinutes(5)
                .registeredAt(REGISTERED_AT)
                .escalatedAt(escalatedAt)
                .status(status)
                .acknowledgedBy(acknowledged ? "Dra. Rojas" : null)
                .acknowledgedAt(acknowledged ? escalatedAt.plusSeconds(30) : null)
                .updatedAt(acknowledged ? escalatedAt.plusSeconds(30) : escalatedAt)
                .build();
    }
}
