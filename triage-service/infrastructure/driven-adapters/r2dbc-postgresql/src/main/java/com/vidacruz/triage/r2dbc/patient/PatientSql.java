package com.vidacruz.triage.r2dbc.patient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * SQL of the patient adapter. Named parameters are expanded by Spring's {@code DatabaseClient}. The writes that
 * change a patient return the stored row, so the answer and the event reflect what was really saved.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PatientSql {

    private static final String COLUMNS = """
            id, full_name, age, heart_rate, systolic_pressure, diastolic_pressure, oxygen_saturation,
            temperature, pain_level, glasgow_coma_scale, chief_complaint, triage_level, sla_minutes, status,
            escalated, registered_at, status_changed_at, consultation_started_at
            """;

    static final String INSERT_PATIENT = """
            INSERT INTO patient (id, full_name, age, heart_rate, systolic_pressure, diastolic_pressure,
                                 oxygen_saturation, temperature, pain_level, glasgow_coma_scale, chief_complaint,
                                 triage_level, sla_minutes, status, escalated, registered_at, status_changed_at,
                                 consultation_started_at)
            VALUES (:id, :fullName, :age, :heartRate, :systolicPressure, :diastolicPressure,
                    :oxygenSaturation, :temperature, :painLevel, :glasgowComaScale, :chiefComplaint,
                    :triageLevel, :slaMinutes, :status, :escalated, :registeredAt, :statusChangedAt,
                    :consultationStartedAt)
            """;

    /**
     * Change of status: applies only if the stored status is still the one the change was computed from. It never
     * writes the escalation flag, so an escalation made meanwhile by the SLA monitor is not overwritten.
     */
    static final String UPDATE_STATUS = """
            UPDATE patient
               SET status = :status,
                   status_changed_at = :statusChangedAt,
                   consultation_started_at = :consultationStartedAt
             WHERE id = :id
               AND status = :previousStatus
            RETURNING\s""" + COLUMNS;

    /** Escalation of RN-03: only a registered patient not escalated yet, so it happens once even with replicas. */
    static final String ESCALATE = """
            UPDATE patient
               SET escalated = TRUE
             WHERE id = :id
               AND status = 'REGISTERED'
               AND NOT escalated
            RETURNING\s""" + COLUMNS;

    /** Registered patients not escalated whose SLA promised at the registration already passed. */
    static final String SELECT_OVERDUE = "SELECT " + COLUMNS + """
              FROM patient
             WHERE status = 'REGISTERED'
               AND NOT escalated
               AND registered_at + make_interval(mins => sla_minutes) < :now
             ORDER BY registered_at
             LIMIT :limit
            """;

    static final String SELECT_BY_ID = "SELECT " + COLUMNS + """
              FROM patient
             WHERE id = :id
            """;

    static final String INSERT_AUDIT = """
            INSERT INTO clinical_audit (patient_id, event_type, previous_status, new_status, occurred_at)
            VALUES (:patientId, :eventType, :previousStatus, :newStatus, :occurredAt)
            """;
}
