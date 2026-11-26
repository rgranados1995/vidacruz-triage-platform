package com.vidacruz.assistant.api.consultation;

import com.vidacruz.assistant.model.answer.AnswerOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * How the clinical questions were answered: {@code assistant_answers_total{outcome}}, one series per outcome of the
 * domain plus {@code unavailable} for the questions answered in degraded mode (503). Every series starts at zero.
 */
@Component
public class ConsultationMetrics {

    static final String ANSWERS = "assistant.answers";
    static final String OUTCOME_TAG = "outcome";
    static final String UNAVAILABLE = "unavailable";
    private static final String DESCRIPTION = "Clinical questions answered, by how the answer was reached";

    private final Map<AnswerOutcome, Counter> byOutcome = new EnumMap<>(AnswerOutcome.class);
    private final Counter unavailable;

    public ConsultationMetrics(MeterRegistry registry) {
        for (var outcome : AnswerOutcome.values()) {
            byOutcome.put(outcome, counter(registry, outcome.name().toLowerCase(Locale.ROOT)));
        }
        unavailable = counter(registry, UNAVAILABLE);
    }

    void answered(AnswerOutcome outcome) {
        byOutcome.get(outcome).increment();
    }

    void unavailable() {
        unavailable.increment();
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder(ANSWERS).description(DESCRIPTION).tag(OUTCOME_TAG, outcome).register(registry);
    }
}
