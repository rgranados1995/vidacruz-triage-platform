package com.vidacruz.assistant.kafka.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

/**
 * What the consumer did with each event of the triage: {@code assistant_urgency_events_total{result}}, where the
 * result is {@code applied} (it changed the projection), {@code ignored_older} (a late or repeated event, older than
 * what is stored) or {@code skipped} (unreadable or of a type the projection does not know). Every series starts at
 * zero.
 */
final class UrgencyEventsMetrics {

    static final String EVENTS = "assistant.urgency.events";
    static final String RESULT_TAG = "result";
    static final String APPLIED = "applied";
    static final String IGNORED_OLDER = "ignored_older";
    static final String SKIPPED = "skipped";
    private static final String DESCRIPTION = "Events of the triage read by the projection, by what was done with them";

    private final Map<Boolean, Counter> projected;
    private final Counter skipped;

    UrgencyEventsMetrics(MeterRegistry registry) {
        projected = Map.of(Boolean.TRUE, counter(registry, APPLIED), Boolean.FALSE, counter(registry, IGNORED_OLDER));
        skipped = counter(registry, SKIPPED);
    }

    /** @param applied whether the event changed the projection */
    void projected(Boolean applied) {
        projected.get(applied).increment();
    }

    void skipped() {
        skipped.increment();
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder(EVENTS).description(DESCRIPTION).tag(RESULT_TAG, result).register(registry);
    }
}
