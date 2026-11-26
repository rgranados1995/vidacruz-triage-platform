package com.vidacruz.triage.usecase.getoperationreport;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.OperationStats;
import com.vidacruz.triage.model.report.ReportPeriod;
import com.vidacruz.triage.model.report.ReportPolicy;
import com.vidacruz.triage.model.report.gateways.OperationReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOperationReportUseCaseTest {

    private static final Instant FROM = Instant.parse("2026-11-25T08:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-25T12:00:00Z");

    @Mock
    private OperationReportRepository repository;

    @Test
    void shouldComputeTheReportOfAValidRange() {
        var period = new ReportPeriod(FROM, TO);
        var report = new OperationReport(period, List.of(),
                OperationStats.builder().registeredByLevel(Map.of()).build());
        when(repository.operationReport(period)).thenReturn(Mono.just(report));

        StepVerifier.create(useCase().getOperationReport(FROM, TO))
                .expectNext(report)
                .verifyComplete();
    }

    @Test
    void shouldRejectAnInvalidRangeWithoutQuerying() {
        StepVerifier.create(useCase().getOperationReport(TO, FROM))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getError() == BusinessError.INVALID_REPORT_PERIOD)
                .verify();
        StepVerifier.create(useCase().getOperationReport(FROM, FROM.plus(Duration.ofDays(2))))
                .expectError(BusinessException.class)
                .verify();

        verifyNoInteractions(repository);
    }

    private GetOperationReportUseCase useCase() {
        return new GetOperationReportUseCase(repository, new ReportPolicy(Duration.ofDays(1)));
    }
}
