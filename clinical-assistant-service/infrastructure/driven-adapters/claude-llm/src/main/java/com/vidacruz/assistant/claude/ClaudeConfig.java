package com.vidacruz.assistant.claude;

import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import com.vidacruz.assistant.model.answer.gateways.AnswerGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the answer generator: Claude when a key is configured, otherwise the degraded one. The SDK does not
 * retry on its own ({@code maxRetries(0)}): retries, timeouts and the circuit breaker belong to the resilience layer.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {

    @Bean
    public AnswerGenerator answerGenerator(ClaudeProperties properties) {
        if (!properties.hasApiKey()) {
            log.warn("ANTHROPIC_API_KEY is not set: the clinical assistant answers questions that need the model "
                    + "as unavailable");
            return new UnavailableAnswerGenerator();
        }
        return new ClaudeAnswerGenerator(anthropicClient(properties), properties);
    }

    static AnthropicClientAsync anthropicClient(ClaudeProperties properties) {
        return AnthropicOkHttpClientAsync.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(properties.timeout())
                .maxRetries(0)
                .build();
    }
}
