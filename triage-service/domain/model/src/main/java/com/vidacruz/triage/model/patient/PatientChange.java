package com.vidacruz.triage.model.patient;

import com.vidacruz.triage.model.audit.AuditEntry;
import com.vidacruz.triage.model.audit.PatientEventType;

import java.time.Instant;

/**
 * A change of a patient together with its audit entry. The repository stores both atomically, so a patient never
 * changes without its audit record (RN-04) and, from the events step on, without its outbox event.
 *
 * @param previousStatus status the stored patient must still have for the change to apply; null for a registration
 */
public record PatientChange(Patient patient, PatientStatus previousStatus, AuditEntry auditEntry) {

    public static PatientChange registration(Patient patient) {
        var entry = new AuditEntry(patient.getId(), PatientEventType.REGISTERED, null, patient.getStatus(),
                patient.getRegisteredAt());
        return new PatientChange(patient, null, entry);
    }

    /**
     * Change of status of RF-02.
     *
     * @throws com.vidacruz.triage.model.exception.BusinessException when the transition is not allowed
     */
    public static PatientChange transition(Patient current, PatientStatus next, Instant at) {
        var moved = current.moveTo(next, at);
        var entry = new AuditEntry(current.getId(), PatientEventType.ofTransitionTo(next), current.getStatus(), next,
                at);
        return new PatientChange(moved, current.getStatus(), entry);
    }

    public boolean isRegistration() {
        return previousStatus == null;
    }
}
