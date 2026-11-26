package com.vidacruz.triage.api.alert;

import com.vidacruz.triage.api.alert.dto.AlertResponse;
import com.vidacruz.triage.contract.ContractCodes;
import com.vidacruz.triage.model.alert.AlertStatus;
import com.vidacruz.triage.model.alert.MedicalAlert;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Translates the alerts to the HTTP contract.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AlertMapper {

    static AlertResponse toResponse(MedicalAlert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .patientId(alert.getPatientId())
                .level(ContractCodes.levelCode(alert.getTriageLevel()))
                .slaMinutes(alert.getSlaMinutes())
                .registeredAt(alert.getRegisteredAt())
                .escalatedAt(alert.getEscalatedAt())
                .minutesWaitedAtEscalation(alert.minutesWaitedAtEscalation())
                .status(statusCode(alert.getStatus()))
                .acknowledgedBy(alert.getAcknowledgedBy())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .resolvedAt(alert.getResolvedAt())
                .updatedAt(alert.getUpdatedAt())
                .build();
    }

    static String statusCode(AlertStatus status) {
        return switch (status) {
            case PENDING -> "PENDIENTE";
            case ACKNOWLEDGED -> "RECONOCIDA";
            case RESOLVED -> "RESUELTA";
        };
    }
}
