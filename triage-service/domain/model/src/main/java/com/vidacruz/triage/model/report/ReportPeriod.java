package com.vidacruz.triage.model.report;

import java.time.Instant;
import java.util.Objects;

/**
 * Range of an operation report: from {@code from} (included) to {@code to} (excluded). Built by {@link ReportPolicy},
 * which checks that the range is valid.
 */
public record ReportPeriod(Instant from, Instant to) {

    public ReportPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
