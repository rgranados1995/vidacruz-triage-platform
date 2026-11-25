package com.vidacruz.triage.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.vidacruz.triage.usecase.publishpendingevents.PublishPendingEventsUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OutboxRelayJobTest {

    @Mock
    private PublishPendingEventsUseCase publishPendingEvents;

    private OutboxRelayJob job;

    @AfterEach
    void stopJob() {
        job.stop();
    }

    @Test
    void shouldRunRoundsWhileStartedAndStopWhenStopped() {
        when(publishPendingEvents.publishPending()).thenReturn(Mono.just(1L));
        job = new OutboxRelayJob(publishPendingEvents, new OutboxRelayProperties(Duration.ofMillis(10)));

        job.start();
        verify(publishPendingEvents, timeout(2000).atLeast(3)).publishPending();
        assertThat(job.isRunning()).isTrue();

        job.stop();
        assertThat(job.isRunning()).isFalse();
        clearInvocations(publishPendingEvents);
        verify(publishPendingEvents, after(200).never()).publishPending();
    }

    @Test
    void shouldKeepRunningAfterAFailedRound(CapturedOutput output) {
        when(publishPendingEvents.publishPending())
                .thenReturn(Mono.error(new IllegalStateException("broker unavailable")))
                .thenReturn(Mono.just(0L));
        job = new OutboxRelayJob(publishPendingEvents, new OutboxRelayProperties(Duration.ofMillis(10)));

        job.start();

        verify(publishPendingEvents, timeout(2000).atLeast(3)).publishPending();
        assertThat(job.isRunning()).isTrue();
        assertThat(output).contains("Outbox relay round failed");
    }

    @Test
    void shouldLogOnlyTheRoundsThatPublishedEvents(CapturedOutput output) {
        var logger = (Logger) LoggerFactory.getLogger(OutboxRelayJob.class);
        var previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            assertLogsOnlyRoundsWithEvents(output);
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private void assertLogsOnlyRoundsWithEvents(CapturedOutput output) {
        when(publishPendingEvents.publishPending()).thenReturn(Mono.just(0L));
        job = new OutboxRelayJob(publishPendingEvents, new OutboxRelayProperties(Duration.ofMillis(10)));
        job.start();
        verify(publishPendingEvents, timeout(2000).atLeast(3)).publishPending();
        assertThat(output).doesNotContain("Outbox relay round");

        job.stop();
        clearInvocations(publishPendingEvents);
        when(publishPendingEvents.publishPending()).thenReturn(Mono.just(2L));
        job.start();

        // Rounds never overlap: the second call only happens after the first round finished and logged
        verify(publishPendingEvents, timeout(2000).atLeast(2)).publishPending();
        assertThat(output).contains("Outbox relay round");
    }

    @Test
    void shouldNotBeRunningBeforeStart() {
        job = new OutboxRelayJob(publishPendingEvents, new OutboxRelayProperties(Duration.ofMillis(10)));

        assertThat(job.isRunning()).isFalse();
    }
}
