package com.vidacruz.triage.api.alert;

import com.vidacruz.triage.model.alert.AlertStatus;
import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertMapperTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");

    @ParameterizedTest
    @CsvSource({"PENDING, PENDIENTE", "ACKNOWLEDGED, RECONOCIDA", "RESOLVED, RESUELTA"})
    void shouldUseTheContractCodeOfEachStatus(AlertStatus status, String code) {
        assertThat(AlertMapper.statusCode(status)).isEqualTo(code);
    }

    @Test
    void shouldCopyEveryFieldOfTheAlert() {
        var patient = UUID.randomUUID();
        var alert = MedicalAlert.builder()
                .id(9)
                .patientId(patient)
                .triageLevel(TriageLevel.URGENT_2)
                .slaMinutes(30)
                .registeredAt(REGISTERED_AT)
                .escalatedAt(REGISTERED_AT.plusSeconds(1830))
                .status(AlertStatus.RESOLVED)
                .acknowledgedBy("Dr. Gómez")
                .acknowledgedAt(REGISTERED_AT.plusSeconds(1900))
                .resolvedAt(REGISTERED_AT.plusSeconds(2000))
                .updatedAt(REGISTERED_AT.plusSeconds(2000))
                .build();

        var response = AlertMapper.toResponse(alert);

        assertThat(response.id()).isEqualTo(9);
        assertThat(response.patientId()).isEqualTo(patient);
        assertThat(response.level()).isEqualTo("URGENTE_2");
        assertThat(response.slaMinutes()).isEqualTo(30);
        assertThat(response.registeredAt()).isEqualTo(REGISTERED_AT);
        assertThat(response.escalatedAt()).isEqualTo(REGISTERED_AT.plusSeconds(1830));
        assertThat(response.minutesWaitedAtEscalation()).isEqualTo(30);
        assertThat(response.status()).isEqualTo("RESUELTA");
        assertThat(response.acknowledgedBy()).isEqualTo("Dr. Gómez");
        assertThat(response.acknowledgedAt()).isEqualTo(REGISTERED_AT.plusSeconds(1900));
        assertThat(response.resolvedAt()).isEqualTo(REGISTERED_AT.plusSeconds(2000));
        assertThat(response.updatedAt()).isEqualTo(REGISTERED_AT.plusSeconds(2000));
    }
}
