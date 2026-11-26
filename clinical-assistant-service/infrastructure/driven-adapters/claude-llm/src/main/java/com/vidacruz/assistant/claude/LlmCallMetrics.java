package com.vidacruz.assistant.claude;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Latency of each attempt to call the AI provider, by how it ended. A retried question records one sample per
 * attempt, and a call the open circuit refuses records none (the provider was not called). Prometheus exposes it as
 * the histogram {@code assistant_llm_latency_seconds{outcome}}, with buckets from half a second to the time limit.
 */
public class LlmCallMetrics {

    static final String LATENCY = "assistant.llm.latency";
    static final String OUTCOME_TAG = "outcome";
    static final String SUCCESS = "success";
    static final String TIMEOUT = "timeout";
    static final String RATE_LIMITED = "rate_limited";
    static final String SERVER_ERROR = "server_error";
    /** The provider refused the request itself (wrong key, malformed request): retrying does not help. */
    static final String REJECTED = "rejected";
    static final String NETWORK = "network";
    static final String OTHER = "other";

    private static final List<String> OUTCOMES =
            List.of(SUCCESS, TIMEOUT, RATE_LIMITED, SERVER_ERROR, REJECTED, NETWORK, OTHER);
    private static final Duration[] BUCKETS = {Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2),
        Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(15), Duration.ofSeconds(30)};
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int REQUEST_TIMEOUT = 408;
    private static final int FIRST_SERVER_ERROR = 500;

    private final MeterRegistry registry;
    private final Map<String, Timer> timers = new HashMap<>();

    public LlmCallMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (var outcome : OUTCOMES) {
            timers.put(outcome, Timer.builder(LATENCY)
                    .description("Latency of each attempt to call the AI provider, by how it ended")
                    .tag(OUTCOME_TAG, outcome)
                    .serviceLevelObjectives(BUCKETS)
                    .register(registry));
        }
    }

    /** Times one attempt, from its subscription until it answers or fails. */
    <T> Mono<T> timed(Mono<T> attempt) {
        return Mono.defer(() -> {
            var sample = Timer.start(registry);
            return attempt
                    .doOnSuccess(answer -> sample.stop(timers.get(SUCCESS)))
                    .doOnError(error -> sample.stop(timers.get(outcomeOf(error))));
        });
    }

    static String outcomeOf(Throwable error) {
        return switch (ProviderFailures.unwrap(error)) {
            case TimeoutException ignored -> TIMEOUT;
            case AnthropicIoException ignored -> NETWORK;
            case AnthropicServiceException service when service.statusCode() == TOO_MANY_REQUESTS -> RATE_LIMITED;
            case AnthropicServiceException service when service.statusCode() == REQUEST_TIMEOUT -> TIMEOUT;
            case AnthropicServiceException service when service.statusCode() >= FIRST_SERVER_ERROR -> SERVER_ERROR;
            case AnthropicServiceException ignored -> REJECTED;
            default -> OTHER;
        };
    }
}
