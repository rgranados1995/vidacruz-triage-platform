package com.vidacruz.triage.scheduler;

import com.vidacruz.triage.usecase.escalateoverduesla.EscalateOverdueSlaUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SlaMonitorJobTest {

    @Mock
    private EscalateOverdueSlaUseCase escalateOverdueSla;

    private SlaMonitorJob job;

    @AfterEach
    void stopJob() {
        job.stop();
    }

    @Test
    void shouldRunTheMonitorAndLogTheEscalationsAtInfo(CapturedOutput output) {
        when(escalateOverdueSla.escalateOverdue()).thenReturn(Mono.just(2L));
        job = new SlaMonitorJob(escalateOverdueSla, new SlaMonitorScheduleProperties(Duration.ofMillis(10)));

        job.start();

        // Rounds never overlap: the second call only happens after the first round finished and logged
        verify(escalateOverdueSla, timeout(2000).atLeast(2)).escalateOverdue();
        assertThat(job.isRunning()).isTrue();
        assertThat(output).contains("Patients escalated for exceeding their SLA");
    }

    @Test
    void shouldNotLogARoundWithoutEscalations(CapturedOutput output) {
        when(escalateOverdueSla.escalateOverdue()).thenReturn(Mono.just(0L));
        job = new SlaMonitorJob(escalateOverdueSla, new SlaMonitorScheduleProperties(Duration.ofMillis(10)));

        job.start();

        verify(escalateOverdueSla, timeout(2000).atLeast(3)).escalateOverdue();
        assertThat(output).doesNotContain("Patients escalated");
    }
}
