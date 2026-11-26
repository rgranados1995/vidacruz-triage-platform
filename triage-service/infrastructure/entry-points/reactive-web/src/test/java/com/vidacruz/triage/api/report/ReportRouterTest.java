package com.vidacruz.triage.api.report;

import com.vidacruz.triage.api.config.SecurityHeadersConfig;
import com.vidacruz.triage.api.error.ApiErrorHandler;
import com.vidacruz.triage.api.error.ApiErrorWriter;
import com.vidacruz.triage.api.security.ApiKeySecurityConfig;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.report.HourlyOperation;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.OperationStats;
import com.vidacruz.triage.model.report.ReportPeriod;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.usecase.getoperationreport.GetOperationReportUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest
@ContextConfiguration(classes = {ReportRouter.class, ReportHandler.class, ApiErrorHandler.class,
        ApiErrorWriter.class, ApiKeySecurityConfig.class, SecurityHeadersConfig.class})
@TestPropertySource(properties = {"routes.operation-report=/triage/reportes/operacion",
        "security.api-key=" + ReportRouterTest.KEY})
class ReportRouterTest {

    static final String KEY = "test-api-key-0001";
    private static final String REPORT = "/triage/reportes/operacion";
    private static final Instant FROM = Instant.parse("2026-11-25T13:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-25T15:00:00Z");

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private GetOperationReportUseCase getOperationReport;

    @Test
    void shouldAnswerTheReportWithTheContractNames() {
        when(getOperationReport.getOperationReport(FROM, TO)).thenReturn(Mono.just(report()));

        get("desde=2026-11-25T08:00:00-05:00&hasta=2026-11-25T15:00:00Z").expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.desde").isEqualTo("2026-11-25T13:00:00Z")
                .jsonPath("$.hasta").isEqualTo("2026-11-25T15:00:00Z")
                .jsonPath("$.horas.length()").isEqualTo(2)
                .jsonPath("$.horas[0].hora").isEqualTo("2026-11-25T13:00:00Z")
                .jsonPath("$.horas[0].totalRegistrados").isEqualTo(3)
                .jsonPath("$.horas[0].porNivel.CRITICO").isEqualTo(1)
                .jsonPath("$.horas[0].porNivel.URGENTE").isEqualTo(2)
                .jsonPath("$.horas[0].porNivel.NO_URGENTE_2").isEqualTo(0)
                .jsonPath("$.horas[0].atendidos").isEqualTo(2)
                .jsonPath("$.horas[0].esperaPromedioMinutos").isEqualTo(12.5)
                .jsonPath("$.horas[0].esperaP95Minutos").isEqualTo(19.25)
                .jsonPath("$.horas[1].totalRegistrados").isEqualTo(0)
                .jsonPath("$.horas[1].esperaPromedioMinutos").isEmpty()
                .jsonPath("$.horas[1].esperaP95Minutos").isEmpty()
                .jsonPath("$.resumen.totalRegistrados").isEqualTo(3)
                .jsonPath("$.resumen.porNivel.URGENTE_2").isEqualTo(0)
                .jsonPath("$.resumen.esperaP95Minutos").isEqualTo(19.25);
    }

    @Test
    void shouldRequireBothEndsOfTheRange() {
        get("hasta=2026-11-25T15:00:00Z").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-004")
                .jsonPath("$.errores[0].campo").isEqualTo("desde")
                .jsonPath("$.errores[0].mensaje").isEqualTo("El parámetro desde es obligatorio");
        get("desde=2026-11-25T08:00:00Z").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errores[0].campo").isEqualTo("hasta");
        verifyNoInteractions(getOperationReport);
    }

    @Test
    void shouldRejectADateWithoutItsZone() {
        get("desde=2026-11-25T08:00:00&hasta=2026-11-25T15:00:00Z").expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-004")
                .jsonPath("$.errores[0].campo").isEqualTo("desde")
                .jsonPath("$.errores[0].mensaje").isEqualTo("El parámetro desde debe ser una fecha y hora ISO 8601 "
                        + "con zona, por ejemplo 2026-11-25T08:00:00-05:00 (en la URL, el signo + se escribe %2B)");
        verifyNoInteractions(getOperationReport);
    }

    @Test
    void shouldAnswerBadRequestForAnInvalidRange() {
        when(getOperationReport.getOperationReport(any(), any())).thenReturn(Mono.error(new BusinessException(
                BusinessError.INVALID_REPORT_PERIOD, "el rango no puede superar 31 días")));

        get("desde=2026-01-01T00:00:00Z&hasta=2026-11-25T15:00:00Z").expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TRG-007")
                .jsonPath("$.title").isEqualTo("Rango de fechas no válido")
                .jsonPath("$.detail").isEqualTo("el rango no puede superar 31 días");
    }

    @Test
    void shouldRequireAValidApiKey() {
        client.get().uri(REPORT + "?desde=2026-11-25T08:00:00Z&hasta=2026-11-25T15:00:00Z").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.codigo").isEqualTo("TRG-005");
        verifyNoInteractions(getOperationReport);
    }

    private WebTestClient.ResponseSpec get(String query) {
        return client.get().uri(REPORT + "?" + query)
                .header(ApiKeySecurityConfig.API_KEY_HEADER, KEY).exchange();
    }

    private static OperationReport report() {
        var busy = OperationStats.builder()
                .registered(3)
                .registeredByLevel(Map.of(TriageLevel.CRITICAL, 1L, TriageLevel.URGENT, 2L))
                .attended(2)
                .averageWaitMinutes(new BigDecimal("12.50"))
                .p95WaitMinutes(new BigDecimal("19.25"))
                .build();
        var empty = OperationStats.builder().registeredByLevel(Map.of()).build();
        return new OperationReport(new ReportPeriod(FROM, TO), List.of(new HourlyOperation(FROM, busy),
                new HourlyOperation(FROM.plusSeconds(3600), empty)), busy);
    }
}
