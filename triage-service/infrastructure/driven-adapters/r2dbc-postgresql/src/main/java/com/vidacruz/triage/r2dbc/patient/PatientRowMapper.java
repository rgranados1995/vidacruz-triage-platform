package com.vidacruz.triage.r2dbc.patient;

import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.triage.TriageLevel;
import io.r2dbc.spi.Readable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Reads a row of {@code patient} into the domain model.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PatientRowMapper {

    static Patient toPatient(Readable row) {
        return Patient.builder()
                .id(row.get("id", UUID.class))
                .fullName(row.get("full_name", String.class))
                .age(row.get("age", Integer.class))
                .vitalSigns(toVitalSigns(row))
                .chiefComplaint(row.get("chief_complaint", String.class))
                .triageLevel(TriageLevel.valueOf(row.get("triage_level", String.class)))
                .slaMinutes(row.get("sla_minutes", Integer.class))
                .status(PatientStatus.valueOf(row.get("status", String.class)))
                .escalated(Boolean.TRUE.equals(row.get("escalated", Boolean.class)))
                .registeredAt(row.get("registered_at", Instant.class))
                .statusChangedAt(row.get("status_changed_at", Instant.class))
                .consultationStartedAt(row.get("consultation_started_at", Instant.class))
                .build();
    }

    private static VitalSigns toVitalSigns(Readable row) {
        return VitalSigns.builder()
                .heartRate(row.get("heart_rate", Integer.class))
                .systolicPressure(row.get("systolic_pressure", Integer.class))
                .diastolicPressure(row.get("diastolic_pressure", Integer.class))
                .oxygenSaturation(row.get("oxygen_saturation", Integer.class))
                .temperature(row.get("temperature", BigDecimal.class))
                .painLevel(row.get("pain_level", Integer.class))
                .glasgowComaScale(row.get("glasgow_coma_scale", Integer.class))
                .build();
    }
}
