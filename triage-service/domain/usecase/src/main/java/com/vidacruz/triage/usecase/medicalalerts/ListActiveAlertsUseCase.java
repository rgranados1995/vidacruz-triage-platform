package com.vidacruz.triage.usecase.medicalalerts;

import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.alert.gateways.MedicalAlertRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * RF-06: the alerts that still need the medical team, the most severe first.
 */
@RequiredArgsConstructor
public class ListActiveAlertsUseCase {

    private final MedicalAlertRepository medicalAlertRepository;

    public Flux<MedicalAlert> listActive() {
        return medicalAlertRepository.findActive();
    }
}
