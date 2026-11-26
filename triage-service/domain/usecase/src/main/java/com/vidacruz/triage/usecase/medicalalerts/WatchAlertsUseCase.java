package com.vidacruz.triage.usecase.medicalalerts;

import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.alert.gateways.MedicalAlertRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * RF-06 in real time: the active alerts at the moment of subscribing, then every change. The changes are subscribed
 * before the active alerts are read, so nothing that happens in between is lost; an alert may arrive twice, and the
 * one with the latest update time is the current one.
 */
@RequiredArgsConstructor
public class WatchAlertsUseCase {

    private final MedicalAlertRepository medicalAlertRepository;

    public Flux<MedicalAlert> watch() {
        return Flux.merge(medicalAlertRepository.changes(), medicalAlertRepository.findActive());
    }
}
