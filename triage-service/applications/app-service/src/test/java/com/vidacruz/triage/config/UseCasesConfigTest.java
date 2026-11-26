package com.vidacruz.triage.config;

import com.vidacruz.triage.model.alert.gateways.MedicalAlertRepository;
import com.vidacruz.triage.model.metrics.gateways.TriageMetrics;
import com.vidacruz.triage.model.outbox.OutboxRelayPolicy;
import com.vidacruz.triage.model.outbox.gateways.EventPublisher;
import com.vidacruz.triage.model.outbox.gateways.OutboxRepository;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.model.report.ReportPolicy;
import com.vidacruz.triage.model.report.gateways.OperationReportRepository;
import com.vidacruz.triage.model.triage.SlaPolicy;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.usecase.changepatientstatus.ChangePatientStatusUseCase;
import com.vidacruz.triage.usecase.escalateoverduesla.EscalateOverdueSlaUseCase;
import com.vidacruz.triage.usecase.getoperationreport.GetOperationReportUseCase;
import com.vidacruz.triage.usecase.getpatient.GetPatientUseCase;
import com.vidacruz.triage.usecase.medicalalerts.AcknowledgeAlertUseCase;
import com.vidacruz.triage.usecase.medicalalerts.ListActiveAlertsUseCase;
import com.vidacruz.triage.usecase.medicalalerts.WatchAlertsUseCase;
import com.vidacruz.triage.usecase.publishpendingevents.PublishPendingEventsUseCase;
import com.vidacruz.triage.usecase.registerpatient.RegisterPatientUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UseCasesConfigTest {

    private static final int NANOS_PER_MICRO = 1000;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UseCasesConfig.class, TriageDomainConfig.class)
            .withBean(PatientRepository.class, () -> mock(PatientRepository.class))
            .withBean(OutboxRepository.class, () -> mock(OutboxRepository.class))
            .withBean(EventPublisher.class, () -> mock(EventPublisher.class))
            .withBean(OperationReportRepository.class, () -> mock(OperationReportRepository.class))
            .withBean(MedicalAlertRepository.class, () -> mock(MedicalAlertRepository.class))
            .withBean(TriageMetrics.class, () -> mock(TriageMetrics.class))
            .withPropertyValues(
                    "triage.sla-minutes.CRITICAL=5",
                    "triage.sla-minutes.URGENT=10",
                    "triage.sla-minutes.URGENT_2=30",
                    "triage.sla-minutes.NON_URGENT=60",
                    "triage.sla-minutes.NON_URGENT_2=120",
                    "triage.administrative-complaints=documentos,renovación de recetario",
                    "outbox.batch-size=50",
                    "sla-monitor.batch-size=20",
                    "outbox.lease=30s",
                    "report.max-range=31d");

    @Test
    void shouldRegisterEveryUseCaseOfTheDomainAsABean() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(RegisterPatientUseCase.class)
                .hasSingleBean(ChangePatientStatusUseCase.class)
                .hasSingleBean(GetPatientUseCase.class)
                .hasSingleBean(PublishPendingEventsUseCase.class)
                .hasSingleBean(EscalateOverdueSlaUseCase.class)
                .hasSingleBean(GetOperationReportUseCase.class)
                .hasSingleBean(ListActiveAlertsUseCase.class)
                .hasSingleBean(WatchAlertsUseCase.class)
                .hasSingleBean(AcknowledgeAlertUseCase.class));
    }

    @Test
    void shouldTakeTheMaximumReportRangeFromTheConfiguration() {
        runner.run(context -> assertThat(context.getBean(ReportPolicy.class))
                .isEqualTo(new ReportPolicy(Duration.ofDays(31))));
    }

    @Test
    void shouldUseAUtcClockWithTheMicrosecondPrecisionOfTheDatabase() {
        runner.run(context -> {
            var clock = context.getBean(Clock.class);
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(clock.instant().getNano() % NANOS_PER_MICRO).isZero();
        });
    }

    @Test
    void shouldTakeTheOutboxPolicyFromTheConfiguration() {
        runner.run(context -> assertThat(context.getBean(OutboxRelayPolicy.class))
                .isEqualTo(new OutboxRelayPolicy(50, Duration.ofSeconds(30))));
    }

    @Test
    void shouldTakeTheSlaOfEachLevelFromTheConfiguration() {
        runner.run(context -> {
            var sla = context.getBean(SlaPolicy.class);
            assertThat(sla.minutesFor(TriageLevel.CRITICAL)).isEqualTo(5);
            assertThat(sla.minutesFor(TriageLevel.NON_URGENT_2)).isEqualTo(120);
        });
    }

    @Test
    void shouldNotStartWhenTheSlaOfALevelIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(TriageDomainConfig.class)
                .withPropertyValues("triage.sla-minutes.CRITICAL=5", "triage.administrative-complaints=documentos")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("URGENT"));
    }

    @Test
    void shouldNotStartWithoutTheAdministrativeComplaints() {
        new ApplicationContextRunner()
                .withUserConfiguration(TriageDomainConfig.class)
                .withPropertyValues("triage.sla-minutes.CRITICAL=5")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .hasMessageContaining("triage.administrative-complaints is required"));
    }
}
