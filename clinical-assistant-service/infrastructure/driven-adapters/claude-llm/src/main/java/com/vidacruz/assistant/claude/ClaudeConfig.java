package com.vidacruz.assistant.claude;

import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import com.vidacruz.assistant.model.answer.gateways.AnswerGenerator;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the answer generator: Claude when a key is configured, otherwise the degraded one. The SDK does not
 * retry on its own ({@code maxRetries(0)}): retries, timeouts and the circuit breaker belong to the resilience
 * policy, configured in {@code resilience4j.*.instances.claude}.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {

    @Bean
    public AnswerGenerator answerGenerator(ClaudeProperties properties, ClaudeResilience resilience) {
        if (!properties.hasApiKey()) {
            log.warn("ANTHROPIC_API_KEY is not set: the clinical assistant answers questions that need the model "
                    + "as unavailable");
            return new UnavailableAnswerGenerator();
        }
        return new ClaudeAnswerGenerator(anthropicClient(properties), properties, resilience);
    }

    @Bean
    public ClaudeResilience claudeResilience(CircuitBreakerRegistry circuitBreakers, RetryRegistry retries,
                                             TimeLimiterRegistry timeLimiters, MeterRegistry meterRegistry) {
        var resilience = new ClaudeResilience(retries.retry(ClaudeResilience.INSTANCE),
                circuitBreakers.circuitBreaker(ClaudeResilience.INSTANCE),
                timeLimiters.timeLimiter(ClaudeResilience.INSTANCE), new LlmCallMetrics(meterRegistry));
        logResilienceEvents(resilience);
        return resilience;
    }

    static AnthropicClientAsync anthropicClient(ClaudeProperties properties) {
        return AnthropicOkHttpClientAsync.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(properties.timeout())
                .maxRetries(0)
                .build();
    }

    /** Every retry and every change of the circuit is a warning: the provider is having trouble. */
    static void logResilienceEvents(ClaudeResilience resilience) {
        resilience.retryPolicy().getEventPublisher().onRetry(event -> log.atWarn()
                .addKeyValue("attempt", event.getNumberOfRetryAttempts())
                .addKeyValue("waitMillis", event.getWaitInterval().toMillis())
                .addKeyValue("cause", String.valueOf(event.getLastThrowable()))
                .log("Retrying the call to Claude"));
        resilience.circuitBreaker().getEventPublisher().onStateTransition(event -> log.atWarn()
                .addKeyValue("from", event.getStateTransition().getFromState())
                .addKeyValue("to", event.getStateTransition().getToState())
                .log("Claude circuit breaker changed state"));
    }
}
