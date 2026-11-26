package com.vidacruz.triage.api.report;

import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.OperationStats;
import com.vidacruz.triage.model.report.ReportPeriod;
import com.vidacruz.triage.usecase.getoperationreport.GetOperationReportUseCase;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Handler without a Spring context: reading of the range and the log of each report.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ReportHandlerTest {

    private static final Instant FROM = Instant.parse("2026-11-25T13:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-25T17:00:00Z");

    @Mock
    private GetOperationReportUseCase getOperationReport;

    @Test
    void shouldReadTheRangeWithItsZoneAndLogTheReport(CapturedOutput output) {
        var summary = OperationStats.builder().registered(4).registeredByLevel(Map.of()).build();
        when(getOperationReport.getOperationReport(FROM, TO))
                .thenReturn(Mono.just(new OperationReport(new ReportPeriod(FROM, TO), List.of(), summary)));
        var request = MockServerRequest.builder()
                .queryParam(ReportHandler.FROM, "2026-11-25T08:00:00-05:00")
                .queryParam(ReportHandler.TO, "2026-11-25T17:00:00Z")
                .build();

        StepVerifier.create(handler().operationReport(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        assertThat(output).contains("Operation report generated");
    }

    @Test
    void shouldNameTheMissingParameter() {
        var request = MockServerRequest.builder().queryParam(ReportHandler.FROM, "2026-11-25T08:00:00Z").build();

        StepVerifier.create(handler().operationReport(request))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(InvalidRequestException.class,
                        invalid -> {
                            assertThat(invalid.getField()).isEqualTo("hasta");
                            assertThat(invalid.getReason()).isEqualTo("El parámetro hasta es obligatorio");
                        }))
                .verify();
        verifyNoInteractions(getOperationReport);
    }

    @Test
    void shouldNameTheParameterThatIsNotADate() {
        var request = MockServerRequest.builder()
                .queryParam(ReportHandler.FROM, "2026-11-25T08:00:00Z")
                .queryParam(ReportHandler.TO, "mañana")
                .build();

        StepVerifier.create(handler().operationReport(request))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(InvalidRequestException.class,
                        invalid -> {
                            assertThat(invalid.getField()).isEqualTo("hasta");
                            assertThat(invalid.getReason()).startsWith("El parámetro hasta debe ser una fecha");
                        }))
                .verify();
        verifyNoInteractions(getOperationReport);
    }

    private ReportHandler handler() {
        return new ReportHandler(getOperationReport);
    }
}
