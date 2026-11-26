package com.vidacruz.triage.r2dbc.report;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * SQL of the operation report (RF-05). PostgreSQL computes every figure; the adapter only maps the result rows.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OperationReportSql {

    /**
     * One row per hour of {@code [:from, :to)}, hours without patients included, plus one last row for the whole
     * range ({@code whole_range}).
     * <ul>
     *     <li>{@code hours}: every hour of the range, in UTC ({@code generate_series}).</li>
     *     <li>{@code registered}: patients registered in the range, with the hour they belong to.</li>
     *     <li>{@code first_consultation}: first entry of each of those patients into the consultation, taken from
     *     the clinical audit ({@code ROW_NUMBER() OVER (PARTITION BY ...)}).</li>
     *     <li>{@code waits}: minutes from the registration to that first entry; null for a patient not attended.</li>
     *     <li>Final select: counts per level with {@code FILTER}, average and 95th percentile of the wait, per hour
     *     and for the whole range at once ({@code GROUPING SETS}). The whole-range percentile comes from the
     *     patients, because hourly percentiles cannot be combined.</li>
     * </ul>
     */
    static final String HOURLY_WITH_SUMMARY = """
            WITH period AS (
                SELECT CAST(:from AS timestamptz) AS starts, CAST(:to AS timestamptz) AS ends
            ),
            hours AS (
                SELECT series.hour
                  FROM period
                 CROSS JOIN generate_series(date_trunc('hour', period.starts, 'UTC'), period.ends,
                                            INTERVAL '1 hour') AS series (hour)
                 WHERE series.hour < period.ends
            ),
            registered AS (
                SELECT p.id, p.triage_level, p.registered_at, date_trunc('hour', p.registered_at, 'UTC') AS hour
                  FROM patient p
                  JOIN period ON p.registered_at >= period.starts AND p.registered_at < period.ends
            ),
            first_consultation AS (
                SELECT ranked.patient_id, ranked.occurred_at
                  FROM (SELECT a.patient_id, a.occurred_at,
                               ROW_NUMBER() OVER (PARTITION BY a.patient_id ORDER BY a.occurred_at, a.id) AS entry
                          FROM clinical_audit a
                          JOIN registered r ON r.id = a.patient_id
                         WHERE a.event_type = 'CONSULTATION_STARTED') AS ranked
                 WHERE ranked.entry = 1
            ),
            waits AS (
                SELECT h.hour, r.id, r.triage_level,
                       EXTRACT(EPOCH FROM fc.occurred_at - r.registered_at) / 60 AS wait_minutes
                  FROM hours h
                  LEFT JOIN registered r ON r.hour = h.hour
                  LEFT JOIN first_consultation fc ON fc.patient_id = r.id
            )
            SELECT w.hour,
                   GROUPING(w.hour) = 1 AS whole_range,
                   COUNT(w.id) AS registered,
                   COUNT(w.id) FILTER (WHERE w.triage_level = 'CRITICAL') AS critical,
                   COUNT(w.id) FILTER (WHERE w.triage_level = 'URGENT') AS urgent,
                   COUNT(w.id) FILTER (WHERE w.triage_level = 'URGENT_2') AS urgent_2,
                   COUNT(w.id) FILTER (WHERE w.triage_level = 'NON_URGENT') AS non_urgent,
                   COUNT(w.id) FILTER (WHERE w.triage_level = 'NON_URGENT_2') AS non_urgent_2,
                   COUNT(w.wait_minutes) AS attended,
                   ROUND(AVG(w.wait_minutes), 2) AS average_wait_minutes,
                   ROUND(CAST(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY w.wait_minutes) AS numeric), 2)
                       AS p95_wait_minutes
              FROM waits w
             GROUP BY GROUPING SETS ((w.hour), ())
             ORDER BY whole_range, w.hour""";
}
