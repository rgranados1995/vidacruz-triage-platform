package com.vidacruz.triage.model.alert;

import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalAlertTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");

    @Test
    void shouldTellTheWholeMinutesWaitedWhenTheAlertWasRaised() {
        var alert = alert(AlertStatus.PENDING).toBuilder().escalatedAt(REGISTERED_AT.plusSeconds(359)).build();

        assertThat(alert.minutesWaitedAtEscalation()).isEqualTo(5);
    }

    @Test
    void shouldExplainWhyAnAlertCannotBeAcknowledged() {
        assertThat(alert(AlertStatus.PENDING).reasonNotAcknowledgeable()).isEmpty();
        assertThat(alert(AlertStatus.ACKNOWLEDGED).reasonNotAcknowledgeable())
                .isEqualTo("la alerta ya fue reconocida por Dra. Rojas");
        assertThat(alert(AlertStatus.RESOLVED).reasonNotAcknowledgeable())
                .isEqualTo("la alerta ya fue resuelta: el paciente fue atendido o dado de alta");
    }

    @Test
    void shouldKeepOnlyPendingAndAcknowledgedAlertsActive() {
        assertThat(AlertStatus.PENDING.isActive()).isTrue();
        assertThat(AlertStatus.ACKNOWLEDGED.isActive()).isTrue();
        assertThat(AlertStatus.RESOLVED.isActive()).isFalse();
    }

    private static MedicalAlert alert(AlertStatus status) {
        return MedicalAlert.builder()
                .id(1)
                .patientId(UUID.randomUUID())
                .triageLevel(TriageLevel.CRITICAL)
                .slaMinutes(5)
                .registeredAt(REGISTERED_AT)
                .escalatedAt(REGISTERED_AT.plusSeconds(330))
                .status(status)
                .acknowledgedBy("Dra. Rojas")
                .updatedAt(REGISTERED_AT.plusSeconds(330))
                .build();
    }
}
