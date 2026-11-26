package com.vidacruz.triage.r2dbc.alert;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * SQL of the escalation alerts. Every write is followed, in its transaction, by a notification on
 * {@link #CHANNEL} with the id of the alert: PostgreSQL delivers it only when the transaction commits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AlertSql {

    static final String CHANNEL = "medical_alert";

    static final String LISTEN = "LISTEN " + CHANNEL;

    static final String NOTIFY = "SELECT pg_notify('" + CHANNEL + "', CAST(:id AS text))";

    private static final String COLUMNS = """
            id, patient_id, triage_level, sla_minutes, registered_at, escalated_at, status, acknowledged_by,
            acknowledged_at, resolved_at, updated_at
            """;

    /** One alert per patient: an escalation that somehow repeats does not raise a second one. */
    static final String RAISE = """
            INSERT INTO medical_alert (patient_id, triage_level, sla_minutes, registered_at, escalated_at, status,
                                       updated_at)
            VALUES (:patientId, :triageLevel, :slaMinutes, :registeredAt, :escalatedAt, 'PENDING', :updatedAt)
            ON CONFLICT (patient_id) DO NOTHING
            RETURNING id
            """;

    static final String RESOLVE = """
            UPDATE medical_alert
               SET status = 'RESOLVED',
                   resolved_at = :resolvedAt,
                   updated_at = :updatedAt
             WHERE patient_id = :patientId
               AND status <> 'RESOLVED'
            RETURNING id
            """;

    /** Applies only to a pending alert, so two doctors taking it at the same time cannot both succeed. */
    static final String ACKNOWLEDGE = """
            UPDATE medical_alert
               SET status = 'ACKNOWLEDGED',
                   acknowledged_by = :acknowledgedBy,
                   acknowledged_at = :acknowledgedAt,
                   updated_at = :updatedAt
             WHERE id = :id
               AND status = 'PENDING'
            RETURNING\s""" + COLUMNS;

    /** The most severe level first, then the oldest escalation. */
    static final String SELECT_ACTIVE = "SELECT " + COLUMNS + """
              FROM medical_alert
             WHERE status <> 'RESOLVED'
             ORDER BY array_position(
                          ARRAY['CRITICAL', 'URGENT', 'URGENT_2', 'NON_URGENT', 'NON_URGENT_2']::varchar[],
                          triage_level),
                      escalated_at, id
            """;

    static final String SELECT_BY_ID = "SELECT " + COLUMNS + """
              FROM medical_alert
             WHERE id = :id
            """;

    static final String SELECT_CHANGED_SINCE = "SELECT " + COLUMNS + """
              FROM medical_alert
             WHERE updated_at >= :since
             ORDER BY updated_at, id
            """;
}
