package com.vidacruz.triage.r2dbc.alert;

import com.vidacruz.triage.model.alert.AlertStatus;
import com.vidacruz.triage.model.triage.TriageLevel;
import io.r2dbc.spi.Readable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mapping of an alert row without a database (the SQL is covered by the integration test).
 */
class AlertRowMapperTest {

    private static final Instant ESCALATED_AT = Instant.parse("2026-11-25T10:05:00Z");

    @Test
    void shouldReadEveryColumnOfTheAlert() {
        var patient = UUID.randomUUID();
        var row = mock(Readable.class);
        when(row.get("id", Long.class)).thenReturn(42L);
        when(row.get("patient_id", UUID.class)).thenReturn(patient);
        when(row.get("triage_level", String.class)).thenReturn("URGENT");
        when(row.get("sla_minutes", Integer.class)).thenReturn(10);
        when(row.get("registered_at", Instant.class)).thenReturn(ESCALATED_AT.minusSeconds(660));
        when(row.get("escalated_at", Instant.class)).thenReturn(ESCALATED_AT);
        when(row.get("status", String.class)).thenReturn("ACKNOWLEDGED");
        when(row.get("acknowledged_by", String.class)).thenReturn("Dra. Rojas");
        when(row.get("acknowledged_at", Instant.class)).thenReturn(ESCALATED_AT.plusSeconds(60));
        when(row.get("resolved_at", Instant.class)).thenReturn(null);
        when(row.get("updated_at", Instant.class)).thenReturn(ESCALATED_AT.plusSeconds(60));

        var alert = AlertRowMapper.toAlert(row);

        assertThat(alert.getId()).isEqualTo(42);
        assertThat(alert.getPatientId()).isEqualTo(patient);
        assertThat(alert.getTriageLevel()).isEqualTo(TriageLevel.URGENT);
        assertThat(alert.getSlaMinutes()).isEqualTo(10);
        assertThat(alert.minutesWaitedAtEscalation()).isEqualTo(11);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(alert.getAcknowledgedBy()).isEqualTo("Dra. Rojas");
        assertThat(alert.getAcknowledgedAt()).isEqualTo(ESCALATED_AT.plusSeconds(60));
        assertThat(alert.getResolvedAt()).isNull();
        assertThat(alert.getUpdatedAt()).isEqualTo(ESCALATED_AT.plusSeconds(60));
    }
}
