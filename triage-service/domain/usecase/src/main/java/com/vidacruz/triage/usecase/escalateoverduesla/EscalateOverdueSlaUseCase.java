package com.vidacruz.triage.usecase.escalateoverduesla;

import com.vidacruz.triage.model.metrics.gateways.TriageMetrics;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.model.triage.SlaMonitorPolicy;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * One round of the SLA monitor (RF-03): escalates the registered patients whose SLA passed without entering the
 * consultation. The rule is checked again in the domain, and the repository applies each escalation only once
 * (RN-03), so concurrent rounds of several replicas never escalate a patient twice.
 */
@RequiredArgsConstructor
public class EscalateOverdueSlaUseCase {

    private final PatientRepository patientRepository;
    private final SlaMonitorPolicy policy;
    private final Clock clock;
    private final TriageMetrics metrics;

    /** Emits how many patients were escalated in this round. */
    public Mono<Long> escalateOverdue() {
        return Mono.fromCallable(clock::instant)
                .flatMapMany(now -> patientRepository.findOverdue(now, policy.batchSize())
                        .filter(patient -> patient.isOverdue(now))
                        .concatMap(patient -> patientRepository.escalate(PatientChange.escalation(patient, now))))
                .doOnNext(escalated -> metrics.patientEscalated(escalated.getTriageLevel()))
                .count();
    }
}
