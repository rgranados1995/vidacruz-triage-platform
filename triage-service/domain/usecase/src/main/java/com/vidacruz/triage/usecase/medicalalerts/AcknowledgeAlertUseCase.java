package com.vidacruz.triage.usecase.medicalalerts;

import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.alert.gateways.MedicalAlertRepository;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.metrics.gateways.TriageMetrics;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * RF-06: a member of the medical team takes a pending alert, so the rest of the team knows someone is on it.
 */
@RequiredArgsConstructor
public class AcknowledgeAlertUseCase {

    private final MedicalAlertRepository medicalAlertRepository;
    private final Clock clock;
    private final TriageMetrics metrics;

    /**
     * Emits the acknowledged alert, or a {@link BusinessException} with {@link BusinessError#ALERT_NOT_FOUND} or
     * {@link BusinessError#ALERT_NOT_ACKNOWLEDGEABLE} when it was already acknowledged or resolved.
     */
    public Mono<MedicalAlert> acknowledge(long alertId, String acknowledgedBy) {
        return Mono.fromCallable(clock::instant)
                .flatMap(now -> medicalAlertRepository.acknowledge(alertId, acknowledgedBy, now))
                .doOnNext(acknowledged -> metrics.alertAcknowledged(acknowledged.getTriageLevel()))
                .switchIfEmpty(Mono.defer(() -> whyNotAcknowledged(alertId)));
    }

    private Mono<MedicalAlert> whyNotAcknowledged(long alertId) {
        return medicalAlertRepository.findById(alertId)
                .switchIfEmpty(Mono.defer(() -> Mono.error(new BusinessException(BusinessError.ALERT_NOT_FOUND))))
                .flatMap(stored -> Mono.error(new BusinessException(BusinessError.ALERT_NOT_ACKNOWLEDGEABLE,
                        stored.reasonNotAcknowledgeable())));
    }
}
