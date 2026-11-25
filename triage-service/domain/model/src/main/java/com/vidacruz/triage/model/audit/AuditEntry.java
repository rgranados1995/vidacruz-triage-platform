package com.vidacruz.triage.model.audit;

import com.vidacruz.triage.model.patient.PatientStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Clinical audit record of RN-04. Append-only: there is no operation to change or delete it.
 *
 * @param previousStatus null for the registration; for an escalation it equals the new status, because the change is
 *                       the flag and not the status
 */
public record AuditEntry(UUID patientId,
                         PatientEventType eventType,
                         PatientStatus previousStatus,
                         PatientStatus newStatus,
                         Instant occurredAt) {
}
