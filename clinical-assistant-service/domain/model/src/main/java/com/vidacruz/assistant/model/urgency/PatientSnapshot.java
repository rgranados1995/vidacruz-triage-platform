package com.vidacruz.assistant.model.urgency;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What an event of the triage says about a patient: every event carries the whole current state, not only the
 * change, so the projection keeps the latest one and an event received twice changes nothing.
 *
 * @param patientId  patient
 * @param level      triage level
 * @param status     clinical status after the event
 * @param occurredAt when the change happened in the triage; orders the events of the same patient
 */
public record PatientSnapshot(UUID patientId, TriageLevel level, PatientStatus status, Instant occurredAt) {

    public PatientSnapshot {
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public boolean isActive() {
        return status.isActive();
    }
}
