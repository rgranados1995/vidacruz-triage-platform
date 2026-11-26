package com.vidacruz.triage.model.report;

import java.util.List;

/**
 * Operation report of RF-05: one entry per hour of the range (hours without patients included) and the figures of
 * the whole range. The summary is computed from the patients, not from the hours, because percentiles do not add up.
 *
 * @param period  range of the report
 * @param hours   figures per hour, in order
 * @param summary figures of the whole range
 */
public record OperationReport(ReportPeriod period, List<HourlyOperation> hours, OperationStats summary) {

    public OperationReport {
        hours = List.copyOf(hours);
    }
}
