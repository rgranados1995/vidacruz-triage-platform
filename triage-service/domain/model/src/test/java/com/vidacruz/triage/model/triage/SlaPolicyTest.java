package com.vidacruz.triage.model.triage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlaPolicyTest {

    /** SLA of RN-02. */
    private static final Map<TriageLevel, Integer> STATEMENT_SLA = Map.of(
            TriageLevel.CRITICAL, 5,
            TriageLevel.URGENT, 10,
            TriageLevel.URGENT_2, 30,
            TriageLevel.NON_URGENT, 60,
            TriageLevel.NON_URGENT_2, 120);

    @ParameterizedTest(name = "{0} -> {1} min")
    @CsvSource({"CRITICAL, 5", "URGENT, 10", "URGENT_2, 30", "NON_URGENT, 60", "NON_URGENT_2, 120"})
    void shouldReturnTheConfiguredMinutesOfEachLevel(TriageLevel level, int minutes) {
        assertThat(new SlaPolicy(STATEMENT_SLA).minutesFor(level)).isEqualTo(minutes);
    }

    @Test
    void shouldRejectAConfigurationWithoutTheSlaOfALevel() {
        var missingLevel = new EnumMap<>(STATEMENT_SLA);
        missingLevel.remove(TriageLevel.URGENT_2);

        assertThatThrownBy(() -> new SlaPolicy(missingLevel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URGENT_2");
    }

    @Test
    void shouldRejectANonPositiveSla() {
        var zero = new EnumMap<>(STATEMENT_SLA);
        zero.put(TriageLevel.CRITICAL, 0);

        assertThatThrownBy(() -> new SlaPolicy(zero))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CRITICAL");
    }

    @Test
    void shouldNotChangeWhenTheSourceMapChanges() {
        var source = new EnumMap<>(STATEMENT_SLA);
        var policy = new SlaPolicy(source);

        source.put(TriageLevel.CRITICAL, 99);

        assertThat(policy.minutesFor(TriageLevel.CRITICAL)).isEqualTo(5);
    }
}
