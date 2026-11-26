package com.vidacruz.assistant.claude;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

/**
 * Settings of the calls to Claude.
 *
 * @param apiKey    key of the Anthropic API, from the environment; blank means the assistant runs degraded
 * @param baseUrl   address of the Anthropic API
 * @param model     model id, such as {@code claude-opus-5-5}
 * @param effort    effort of the model: low, medium, high, xhigh or max
 * @param maxTokens longest answer, in tokens
 * @param timeout   longest wait for an answer
 */
@ConfigurationProperties(prefix = "adapters.claude")
public record ClaudeProperties(String apiKey, String baseUrl, String model, String effort, long maxTokens,
                               Duration timeout) {

    private static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

    public ClaudeProperties {
        if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("adapters.claude.base-url and adapters.claude.model are required");
        }
        if (!EFFORTS.contains(effort)) {
            throw new IllegalArgumentException("adapters.claude.effort must be one of " + EFFORTS + ", but it is "
                    + effort);
        }
        if (maxTokens <= 0 || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("adapters.claude.max-tokens and adapters.claude.timeout must be "
                    + "positive");
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never prints the key. */
    @Override
    public String toString() {
        return "ClaudeProperties[apiKey=" + (hasApiKey() ? "****" : "") + ", baseUrl=" + baseUrl + ", model=" + model
                + ", effort=" + effort + ", maxTokens=" + maxTokens + ", timeout=" + timeout + "]";
    }
}
