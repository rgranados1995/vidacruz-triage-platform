package com.vidacruz.triage.model.metrics.gateways;

import com.vidacruz.triage.model.triage.TriageLevel;

/**
 * Business metrics of the triage: what happened in the emergency room, by triage level. Each call records one fact
 * that already happened (it is stored), so a failed write never counts.
 */
public interface TriageMetrics {

    /** A patient was registered and classified with this level (RF-01). */
    void patientClassified(TriageLevel level);

    /** The SLA of a patient of this level passed and the escalation was applied (RF-03). */
    void patientEscalated(TriageLevel level);

    /** A member of the medical team took the alert of a patient of this level (RF-06). */
    void alertAcknowledged(TriageLevel level);
}
