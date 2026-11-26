package com.vidacruz.triage.r2dbc.alert;

import com.vidacruz.triage.model.alert.AlertStatus;
import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.triage.TriageLevel;
import io.r2dbc.spi.Readable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Reads a row of {@code medical_alert} into the domain model.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AlertRowMapper {

    static MedicalAlert toAlert(Readable row) {
        return MedicalAlert.builder()
                .id(row.get("id", Long.class))
                .patientId(row.get("patient_id", UUID.class))
                .triageLevel(TriageLevel.valueOf(row.get("triage_level", String.class)))
                .slaMinutes(row.get("sla_minutes", Integer.class))
                .registeredAt(row.get("registered_at", Instant.class))
                .escalatedAt(row.get("escalated_at", Instant.class))
                .status(AlertStatus.valueOf(row.get("status", String.class)))
                .acknowledgedBy(row.get("acknowledged_by", String.class))
                .acknowledgedAt(row.get("acknowledged_at", Instant.class))
                .resolvedAt(row.get("resolved_at", Instant.class))
                .updatedAt(row.get("updated_at", Instant.class))
                .build();
    }
}
