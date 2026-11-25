package com.vidacruz.triage.model.triage;

/**
 * How the SLA monitor (RF-03) works through the overdue patients.
 *
 * @param batchSize maximum patients escalated per round; the rest wait for the next round
 */
public record SlaMonitorPolicy(int batchSize) {

    public SlaMonitorPolicy {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("The SLA monitor batch size must be positive, but it is " + batchSize);
        }
    }
}
