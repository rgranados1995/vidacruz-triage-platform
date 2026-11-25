package com.vidacruz.triage.model.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRelayPolicyTest {

    @Test
    void shouldKeepAValidPolicy() {
        var policy = new OutboxRelayPolicy(1, Duration.ofMillis(1));

        assertThat(policy.batchSize()).isEqualTo(1);
        assertThat(policy.lease()).isEqualTo(Duration.ofMillis(1));
    }

    @ParameterizedTest(name = "batch size {0} is rejected")
    @ValueSource(ints = {0, -1})
    void shouldRejectANonPositiveBatchSize(int batchSize) {
        var lease = Duration.ofSeconds(30);

        assertThatThrownBy(() -> new OutboxRelayPolicy(batchSize, lease))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch size");
    }

    @ParameterizedTest(name = "lease of {0} ms is rejected")
    @ValueSource(longs = {0, -1})
    void shouldRejectANonPositiveLease(long millis) {
        var lease = Duration.ofMillis(millis);

        assertThatThrownBy(() -> new OutboxRelayPolicy(10, lease))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease");
    }

    @Test
    void shouldRejectAMissingLease() {
        assertThatThrownBy(() -> new OutboxRelayPolicy(10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease");
    }
}
