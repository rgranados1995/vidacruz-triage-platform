package com.vidacruz.triage.config;

import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.model.triage.SlaPolicy;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.usecase.changepatientstatus.ChangePatientStatusUseCase;
import com.vidacruz.triage.usecase.getpatient.GetPatientUseCase;
import com.vidacruz.triage.usecase.registerpatient.RegisterPatientUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UseCasesConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UseCasesConfig.class, TriageDomainConfig.class)
            .withBean(PatientRepository.class, () -> mock(PatientRepository.class))
            .withPropertyValues(
                    "triage.sla-minutes.CRITICAL=5",
                    "triage.sla-minutes.URGENT=10",
                    "triage.sla-minutes.URGENT_2=30",
                    "triage.sla-minutes.NON_URGENT=60",
                    "triage.sla-minutes.NON_URGENT_2=120",
                    "triage.administrative-complaints=documentos,renovación de recetario");

    @Test
    void shouldRegisterEveryUseCaseOfTheDomainAsABean() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(RegisterPatientUseCase.class)
                .hasSingleBean(ChangePatientStatusUseCase.class)
                .hasSingleBean(GetPatientUseCase.class));
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
