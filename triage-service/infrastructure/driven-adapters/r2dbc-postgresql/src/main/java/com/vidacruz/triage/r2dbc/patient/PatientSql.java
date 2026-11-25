package com.vidacruz.triage.r2dbc.patient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * SQL of the patient adapter. Named parameters are expanded by Spring's {@code DatabaseClient}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PatientSql {

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

    /** Applies the change only if the stored status is still the one the change was computed from. */
    static final String UPDATE_PATIENT = """
            UPDATE patient
               SET status = :status,
                   escalated = :escalated,
                   status_changed_at = :statusChangedAt,
                   consultation_started_at = :consultationStartedAt
             WHERE id = :id
               AND status = :previousStatus
            """;

    static final String SELECT_BY_ID = """
            SELECT id, full_name, age, heart_rate, systolic_pressure, diastolic_pressure, oxygen_saturation,
                   temperature, pain_level, glasgow_coma_scale, chief_complaint, triage_level, sla_minutes, status,
                   escalated, registered_at, status_changed_at, consultation_started_at
              FROM patient
             WHERE id = :id
            """;

    static final String INSERT_AUDIT = """
            INSERT INTO clinical_audit (patient_id, event_type, previous_status, new_status, occurred_at)
            VALUES (:patientId, :eventType, :previousStatus, :newStatus, :occurredAt)
            """;
}
