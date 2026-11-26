package com.vidacruz.triage.model.report;

import java.time.Instant;

/**
 * Figures of the patients registered during one hour.
 *
 * @param hour  start of the hour (UTC)
 * @param stats figures of the patients registered in that hour
 */
public record HourlyOperation(Instant hour, OperationStats stats) {
}
