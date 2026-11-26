package com.vidacruz.triage.model.report;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;

import java.time.Duration;
import java.time.Instant;

/**
 * Limits of the operation report of RF-05. The maximum range bounds the cost of the query and the size of the
 * answer (one row per hour).
 *
 * @param maxRange longest range a report may cover
 */
public record ReportPolicy(Duration maxRange) {

    private static final long MINUTES_PER_HOUR = 60;
    private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    public ReportPolicy {
        if (maxRange == null || maxRange.isNegative() || maxRange.isZero()) {
            throw new IllegalArgumentException("The maximum report range must be positive, but it is " + maxRange);
        }
    }

    /**
     * Checks the range asked by the staff.
     *
     * @throws BusinessException with {@link BusinessError#INVALID_REPORT_PERIOD} when {@code from} is not before
     *                           {@code to} or the range is longer than the maximum
     */
    public ReportPeriod periodBetween(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new BusinessException(BusinessError.INVALID_REPORT_PERIOD,
                    "la fecha desde debe ser anterior a la fecha hasta");
        }
        if (Duration.between(from, to).compareTo(maxRange) > 0) {
            throw new BusinessException(BusinessError.INVALID_REPORT_PERIOD,
                    "el rango no puede superar " + describe(maxRange));
        }
        return new ReportPeriod(from, to);
    }

    /** The range in the largest whole unit, as the staff reads it: "31 días", "12 horas" or "90 minutos". */
    private static String describe(Duration range) {
        var minutes = range.toMinutes();
        if (minutes % MINUTES_PER_DAY == 0) {
            return minutes / MINUTES_PER_DAY + " días";
        }
        return minutes % MINUTES_PER_HOUR == 0 ? minutes / MINUTES_PER_HOUR + " horas" : minutes + " minutos";
    }
}
