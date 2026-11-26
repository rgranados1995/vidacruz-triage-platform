package com.vidacruz.triage.model.alert;

import com.vidacruz.triage.model.triage.TriageLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Alert to the medical team for a patient who exceeded the SLA without entering the consultation (RF-06). It is
 * created together with the escalation, so there is exactly one per escalated patient. It carries no personal data:
 * the staff opens the patient to see who it is.
 */
@Getter
@Builder(toBuilder = true)
public class MedicalAlert {

    private final long id;
    private final UUID patientId;
    private final TriageLevel triageLevel;
    private final int slaMinutes;
    private final Instant registeredAt;
    private final Instant escalatedAt;
    private final AlertStatus status;
    /** Who took the alert, as reported by the client; null while pending. */
    private final String acknowledgedBy;
    private final Instant acknowledgedAt;
    private final Instant resolvedAt;
    /** Time of the last change: a client that receives the same alert twice keeps the most recent one. */
    private final Instant updatedAt;

    /** Whole minutes the patient had waited when the alert was raised. */
    public long minutesWaitedAtEscalation() {
        return Duration.between(registeredAt, escalatedAt).toMinutes();
    }

    /** Explanation for the staff (in Spanish) of why the alert cannot be acknowledged; empty when it can. */
    public String reasonNotAcknowledgeable() {
        return switch (status) {
            case PENDING -> "";
            case ACKNOWLEDGED -> "la alerta ya fue reconocida por " + acknowledgedBy;
            case RESOLVED -> "la alerta ya fue resuelta: el paciente fue atendido o dado de alta";
        };
    }
}
