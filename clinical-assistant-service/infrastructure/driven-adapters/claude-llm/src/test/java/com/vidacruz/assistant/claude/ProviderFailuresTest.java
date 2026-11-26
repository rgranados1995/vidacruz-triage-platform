package com.vidacruz.assistant.claude;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderFailuresTest {

    @ParameterizedTest
    @CsvSource({
        "429, true, true", "408, true, true", "500, true, true", "529, true, true", "503, true, true",
        "401, false, true", "403, false, true",
        "400, false, false", "404, false, false", "413, false, false", "422, false, false", "499, false, false"})
    void shouldClassifyEveryAnswerOfTheProvider(int status, boolean retried, boolean opensTheCircuit) {
        var failure = status(status);

        assertThat(new ProviderFailures.Transient().test(failure)).isEqualTo(retried);
        assertThat(new ProviderFailures.Outage().test(failure)).isEqualTo(opensTheCircuit);
    }

    @Test
    void shouldRetryTimeoutsAndNetworkFailuresEvenWhenWrapped() {
        var timeout = new TimeoutException("attempt took too long");
        var network = new AnthropicIoException("connection refused", null);

        assertThat(ProviderFailures.isTransient(timeout)).isTrue();
        assertThat(ProviderFailures.isTransient(network)).isTrue();
        assertThat(ProviderFailures.isTransient(new CompletionException(network))).isTrue();
        assertThat(ProviderFailures.isTransient(new ExecutionException(new CompletionException(timeout)))).isTrue();
        assertThat(ProviderFailures.isOutage(new CompletionException(status(529)))).isTrue();
    }

    @Test
    void shouldIgnoreFailuresOfOurOwn() {
        assertThat(ProviderFailures.isTransient(new IllegalStateException("bug"))).isFalse();
        assertThat(ProviderFailures.isOutage(new IllegalStateException("bug"))).isFalse();
        assertThat(ProviderFailures.isOutage(new CompletionException(null))).isFalse();
    }

    private static AnthropicServiceException status(int code) {
        var failure = mock(AnthropicServiceException.class);
        when(failure.statusCode()).thenReturn(code);
        return failure;
    }
}
