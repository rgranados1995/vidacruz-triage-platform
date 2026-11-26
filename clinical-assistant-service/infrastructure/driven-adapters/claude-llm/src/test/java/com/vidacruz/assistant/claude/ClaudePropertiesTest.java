package com.vidacruz.assistant.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClaudePropertiesTest {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String MODEL = "claude-opus-5-5";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @ParameterizedTest
    @ValueSource(strings = {"low", "medium", "high", "xhigh", "max"})
    void shouldAcceptEveryEffortOfTheApi(String effort) {
        assertThat(new ClaudeProperties("", BASE_URL, MODEL, effort, 2048, TIMEOUT).effort()).isEqualTo(effort);
    }

    @Test
    void shouldRequireTheAddressTheModelAndAKnownEffort() {
        assertThatIllegalArgumentException().isThrownBy(() -> endpoint(" ", MODEL, "medium"));
        assertThatIllegalArgumentException().isThrownBy(() -> endpoint(null, MODEL, "medium"));
        assertThatIllegalArgumentException().isThrownBy(() -> endpoint(BASE_URL, "", "medium"));
        assertThatIllegalArgumentException().isThrownBy(() -> endpoint(BASE_URL, null, "medium"));
        assertThatIllegalArgumentException().isThrownBy(() -> endpoint(BASE_URL, MODEL, "extreme"));
    }

    @Test
    void shouldRequireAPositiveAnswerSizeAndTimeout() {
        var zero = Duration.ZERO;
        var negative = Duration.ofSeconds(-1);

        assertThatIllegalArgumentException().isThrownBy(() -> limits(0, TIMEOUT));
        assertThatIllegalArgumentException().isThrownBy(() -> limits(1, zero));
        assertThatIllegalArgumentException().isThrownBy(() -> limits(1, negative));
        assertThatIllegalArgumentException().isThrownBy(() -> limits(1, null));
        assertThat(limits(1, TIMEOUT).maxTokens()).isEqualTo(1);
    }

    @Test
    void shouldNeverPrintTheKey() {
        var configured = new ClaudeProperties("secret-value-123", BASE_URL, MODEL, "low", 1, TIMEOUT);
        var missing = new ClaudeProperties(null, BASE_URL, MODEL, "low", 1, TIMEOUT);

        assertThat(configured.hasApiKey()).isTrue();
        assertThat(configured.toString()).doesNotContain("secret-value-123").contains("apiKey=****");
        assertThat(missing.hasApiKey()).isFalse();
        assertThat(missing.toString()).contains("apiKey=,");
    }

    private static ClaudeProperties endpoint(String baseUrl, String model, String effort) {
        return new ClaudeProperties("", baseUrl, model, effort, 1, TIMEOUT);
    }

    private static ClaudeProperties limits(long maxTokens, Duration timeout) {
        return new ClaudeProperties("", BASE_URL, MODEL, "medium", maxTokens, timeout);
    }
}
