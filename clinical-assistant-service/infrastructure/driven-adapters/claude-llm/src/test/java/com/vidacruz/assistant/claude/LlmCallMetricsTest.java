package com.vidacruz.assistant.claude;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCallMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LlmCallMetrics metrics = new LlmCallMetrics(registry);

    @Test
    void shouldTimeEachAttemptUnderHowItEnded() {
        StepVerifier.create(metrics.timed(Mono.delay(Duration.ofMillis(50)))).expectNextCount(1).verifyComplete();
        StepVerifier.create(metrics.timed(Mono.error(new TimeoutException("too slow")))).verifyError();
        StepVerifier.create(metrics.timed(Mono.error(status(529)))).verifyError();

        assertThat(timer(LlmCallMetrics.SUCCESS).count()).isEqualTo(1);
        assertThat(timer(LlmCallMetrics.SUCCESS).totalTime(TimeUnit.MILLISECONDS))
                .isGreaterThanOrEqualTo(50);
        assertThat(timer(LlmCallMetrics.TIMEOUT).count()).isEqualTo(1);
        assertThat(timer(LlmCallMetrics.SERVER_ERROR).count()).isEqualTo(1);
        assertThat(timer(LlmCallMetrics.REJECTED).count()).isZero();
    }

    @Test
    void shouldStartTimingOnlyWhenTheAttemptStarts() {
        var attempt = metrics.timed(Mono.just("answer"));

        assertThat(timer(LlmCallMetrics.SUCCESS).count()).isZero();
        StepVerifier.create(attempt).expectNext("answer").verifyComplete();
        StepVerifier.create(attempt).expectNext("answer").verifyComplete();
        assertThat(timer(LlmCallMetrics.SUCCESS).count()).isEqualTo(2);
    }

    @Test
    void shouldExposeTheBucketsOfTheHistogram() {
        var buckets = timer(LlmCallMetrics.SUCCESS).takeSnapshot().histogramCounts();

        assertThat(buckets).extracting(bucket -> bucket.bucket(TimeUnit.SECONDS))
                .containsExactly(0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0);
    }

    @ParameterizedTest
    @CsvSource({"429, rate_limited", "408, timeout", "500, server_error", "529, server_error", "401, rejected",
        "400, rejected"})
    void shouldNameTheOutcomeOfEachAnswerOfTheProvider(int code, String outcome) {
        assertThat(LlmCallMetrics.outcomeOf(new CompletionException(status(code)))).isEqualTo(outcome);
    }

    @Test
    void shouldNameTheOutcomeOfFailuresWithoutAnAnswer() {
        assertThat(LlmCallMetrics.outcomeOf(new AnthropicIoException("connection refused", null)))
                .isEqualTo(LlmCallMetrics.NETWORK);
        assertThat(LlmCallMetrics.outcomeOf(new TimeoutException("too slow"))).isEqualTo(LlmCallMetrics.TIMEOUT);
        assertThat(LlmCallMetrics.outcomeOf(new IllegalStateException("bug"))).isEqualTo(LlmCallMetrics.OTHER);
    }

    private Timer timer(String outcome) {
        return registry.get(LlmCallMetrics.LATENCY).tag(LlmCallMetrics.OUTCOME_TAG, outcome).timer();
    }

    private static AnthropicServiceException status(int code) {
        var failure = mock(AnthropicServiceException.class);
        when(failure.statusCode()).thenReturn(code);
        return failure;
    }
}
