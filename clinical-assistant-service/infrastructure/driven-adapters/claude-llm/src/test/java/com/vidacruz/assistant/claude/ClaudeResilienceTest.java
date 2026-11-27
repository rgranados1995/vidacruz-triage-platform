package com.vidacruz.assistant.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.vidacruz.assistant.model.answer.ExcerptSet;
import com.vidacruz.assistant.model.answer.Claim;
import com.vidacruz.assistant.model.answer.GeneratedAnswer;
import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The resilience policy against a fake Anthropic API, with the shape of the deployment (2 attempts, circuit of 10
 * calls that opens at 50 % with at least 5) and short times.
 */
@ExtendWith(OutputCaptureExtension.class)
class ClaudeResilienceTest {

    @RegisterExtension
    static final WireMockExtension ANTHROPIC = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String MESSAGES = "/v1/messages";
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofMillis(1500);
    private static final Duration OPEN_WAIT = Duration.ofMillis(400);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ClinicalQuestion QUESTION = ClinicalQuestion.from("¿Cuándo se administra el antibiótico?");
    private static final ExcerptSet EXCERPTS = new ExcerptSet(List.of(new ProtocolExcerpt("PROTO-002",
            "Sepsis, identificación temprana (qSOFA)", "Acciones iniciales",
            "Antibiótico de amplio espectro en la primera hora.", 0.74)));

    private ClaudeResilience resilience;
    private ClaudeAnswerGenerator generator;

    @BeforeEach
    void newPolicy() {
        resilience = resilience(OPEN_WAIT, ATTEMPT_TIMEOUT);
        var properties = new ClaudeProperties("test-anthropic-key", ANTHROPIC.baseUrl(), "claude-opus-5-5", "medium",
                2048, Duration.ofSeconds(5));
        generator = new ClaudeAnswerGenerator(ClaudeConfig.anthropicClient(properties), properties, resilience);
    }

    @Test
    void shouldRetryATransientFailureAndAnswer(CapturedOutput output) throws Exception {
        ClaudeConfig.logResilienceEvents(resilience);
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).inScenario("overloaded once")
                .whenScenarioStateIs(Scenario.STARTED).willReturn(overloaded()).willSetStateTo("recovered"));
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).inScenario("overloaded once")
                .whenScenarioStateIs("recovered").willReturn(answered()));

        StepVerifier.create(generator.generate(QUESTION, EXCERPTS))
                .expectNext(new GeneratedAnswer(false, List.of(new Claim("En la primera hora.", List.of("E1")))))
                .verifyComplete();

        ANTHROPIC.verify(2, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(output).contains("Retrying the call to Claude");
    }

    @Test
    void shouldNotRetryARequestThatIsWrong() {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(error(400, "invalid_request_error")));

        StepVerifier.create(generator.generate(QUESTION, EXCERPTS)).expectErrorMatches(this::unavailable).verify();

        ANTHROPIC.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES)));
    }

    @Test
    void shouldGiveUpOnASlowProviderWithinTheAttemptTimeouts() throws Exception {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(answered().withFixedDelay(4000)));

        StepVerifier.create(generator.generate(QUESTION, EXCERPTS))
                .expectErrorSatisfies(error -> {
                    assertThat(unavailable(error)).isTrue();
                    assertThat(error.getCause()).isInstanceOf(TimeoutException.class);
                })
                .verify(Duration.ofSeconds(5));

        ANTHROPIC.verify(2, postRequestedFor(urlPathEqualTo(MESSAGES)));
    }

    @Test
    void shouldCancelTheHttpCallOfAnAttemptThatTimedOut() {
        // The provider fails only after the time limit: a call still running would fail with nobody listening
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(overloaded().withFixedDelay(2500)));
        var dropped = new CopyOnWriteArrayList<Throwable>();
        Hooks.onErrorDropped(dropped::add);
        try {
            StepVerifier.create(generator.generate(QUESTION, EXCERPTS)).expectErrorMatches(this::unavailable)
                    .verify(Duration.ofSeconds(5));
            StepVerifier.create(Mono.delay(Duration.ofMillis(2000))).expectNextCount(1).verifyComplete();
        } finally {
            Hooks.resetOnErrorDropped();
        }

        assertThat(dropped).isEmpty();
    }

    @Test
    void shouldOpenTheCircuitAndStopCallingTheProvider(CapturedOutput output) {
        ClaudeConfig.logResilienceEvents(resilience);
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(overloaded()));

        // Three questions: 2 + 2 + 1 failed calls, the circuit opens on the fifth and the last attempt is refused
        for (var round = 0; round < 3; round++) {
            StepVerifier.create(generator.generate(QUESTION, EXCERPTS)).expectErrorMatches(this::unavailable).verify();
        }
        assertThat(resilience.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        ANTHROPIC.verify(5, postRequestedFor(urlPathEqualTo(MESSAGES)));

        StepVerifier.create(generator.generate(QUESTION, EXCERPTS))
                .expectErrorSatisfies(error -> {
                    assertThat(unavailable(error)).isTrue();
                    assertThat(error.getCause()).isInstanceOf(CallNotPermittedException.class);
                })
                .verify();
        ANTHROPIC.verify(5, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(output).contains("Claude circuit breaker changed state");
    }

    @Test
    void shouldCloseTheCircuitAgainOnceTheProviderAnswers() throws Exception {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(overloaded()));
        for (var round = 0; round < 3; round++) {
            StepVerifier.create(generator.generate(QUESTION, EXCERPTS)).expectErrorMatches(this::unavailable).verify();
        }
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(answered()));

        StepVerifier.create(Mono.delay(OPEN_WAIT.plusMillis(100))
                        .then(generator.generate(QUESTION, EXCERPTS))
                        .then(generator.generate(QUESTION, EXCERPTS)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(resilience.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldCountARejectedKeyForTheCircuitWithoutRetryingIt() {
        ANTHROPIC.stubFor(post(urlPathEqualTo(MESSAGES)).willReturn(error(401, "authentication_error")));

        StepVerifier.create(generator.generate(QUESTION, EXCERPTS)).expectErrorMatches(this::unavailable).verify();

        ANTHROPIC.verify(1, postRequestedFor(urlPathEqualTo(MESSAGES)));
        assertThat(resilience.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    /** Same shape as the {@code claude} instances of application.yaml, with short times. */
    static ClaudeResilience resilience(Duration openWait, Duration attemptTimeout) {
        var retry = Retry.of(ClaudeResilience.INSTANCE, RetryConfig.custom()
                .maxAttempts(2)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(20), 2, 0.5))
                .retryOnException(new ProviderFailures.Transient())
                .build());
        var circuitBreaker = CircuitBreaker.of(ClaudeResilience.INSTANCE, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(openWait)
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(new ProviderFailures.Outage())
                .build());
        var timeLimiter = TimeLimiter.of(ClaudeResilience.INSTANCE, TimeLimiterConfig.custom()
                .timeoutDuration(attemptTimeout)
                .cancelRunningFuture(true)
                .build());
        return new ClaudeResilience(retry, circuitBreaker, timeLimiter, new LlmCallMetrics(new SimpleMeterRegistry()));
    }

    private boolean unavailable(Throwable error) {
        return error instanceof BusinessException business
                && business.getError() == BusinessError.ASSISTANT_UNAVAILABLE;
    }

    private static ResponseDefinitionBuilder overloaded() {
        return error(529, "overloaded_error");
    }

    private static ResponseDefinitionBuilder error(int status, String type) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody("{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"test\"}}");
    }

    private static ResponseDefinitionBuilder answered() throws Exception {
        var text = JSON.writeValueAsString(Map.of("noProtocol", false, "claims",
                List.of(Map.of("text", "En la primera hora.", "excerptIds", List.of("E1")))));
        return okJson(JSON.writeValueAsString(Map.of(
                "id", "msg_test", "type", "message", "role", "assistant", "model", "claude-opus-5-5",
                "content", List.of(Map.of("type", "text", "text", text)), "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 120, "output_tokens", 20))));
    }
}
