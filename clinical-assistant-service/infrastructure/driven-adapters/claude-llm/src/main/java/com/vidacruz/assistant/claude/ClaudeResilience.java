package com.vidacruz.assistant.claude;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import reactor.core.publisher.Mono;

/**
 * Resilience policy of the calls to Claude, in the order Resilience4j recommends (outermost first): the retry
 * repeats only transient failures, each attempt goes through the circuit breaker (an open circuit answers at once,
 * without calling the API, and is never retried), and each attempt has its own time limit.
 */
public record ClaudeResilience(Retry retryPolicy, CircuitBreaker circuitBreaker, TimeLimiter timeLimiter) {

    /** Name of the instances in the configuration ({@code resilience4j.*.instances.claude}). */
    public static final String INSTANCE = "claude";

    <T> Mono<T> protect(Mono<T> call) {
        return call
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retryPolicy));
    }
}
