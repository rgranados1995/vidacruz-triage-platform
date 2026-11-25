package com.vidacruz.triage.r2dbc.patient;

import com.vidacruz.triage.model.audit.AuditEntry;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;

import java.time.Instant;

/**
 * Binds the domain objects to the named parameters of {@link PatientSql}. Enums are stored by their domain name.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PatientBinder {

    static GenericExecuteSpec bindNewPatient(GenericExecuteSpec spec, Patient patient) {
        var signs = patient.getVitalSigns();
        var withVitalSigns = spec
                .bind("heartRate", signs.heartRate())
                .bind("systolicPressure", signs.systolicPressure())
                .bind("diastolicPressure", signs.diastolicPressure())
                .bind("oxygenSaturation", signs.oxygenSaturation())
                .bind("temperature", signs.temperature())
                .bind("painLevel", signs.painLevel())
                .bind("glasgowComaScale", signs.glasgowComaScale());
        var withIdentity = withVitalSigns
                .bind("id", patient.getId())
                .bind("fullName", patient.getFullName())
                .bind("age", patient.getAge())
                .bind("chiefComplaint", patient.getChiefComplaint())
                .bind("triageLevel", patient.getTriageLevel().name())
                .bind("slaMinutes", patient.getSlaMinutes())
                .bind("escalated", patient.isEscalated())
                .bind("registeredAt", patient.getRegisteredAt());
        return bindState(withIdentity, patient);
    }

    static GenericExecuteSpec bindChange(GenericExecuteSpec spec, PatientChange change) {
        var withCondition = spec
                .bind("id", change.patient().getId())
                .bind("previousStatus", change.previousStatus().name());
        return bindState(withCondition, change.patient());
    }

    static GenericExecuteSpec bindAudit(GenericExecuteSpec spec, AuditEntry entry) {
        var withEntry = spec
                .bind("patientId", entry.patientId())
                .bind("eventType", entry.eventType().name())
                .bind("newStatus", entry.newStatus().name())
                .bind("occurredAt", entry.occurredAt());
        var previous = entry.previousStatus();
        return nullable(withEntry, "previousStatus", previous == null ? null : previous.name(), String.class);
    }

    private static GenericExecuteSpec bindState(GenericExecuteSpec spec, Patient patient) {
        var withState = spec
                .bind("status", patient.getStatus().name())
                .bind("statusChangedAt", patient.getStatusChangedAt());
        return nullable(withState, "consultationStartedAt", patient.getConsultationStartedAt(), Instant.class);
    }

    private static GenericExecuteSpec nullable(GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
