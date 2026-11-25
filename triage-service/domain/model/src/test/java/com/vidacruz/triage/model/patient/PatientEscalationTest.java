package com.vidacruz.triage.model.patient;

import com.vidacruz.triage.model.audit.PatientEventType;
import com.vidacruz.triage.model.triage.SlaMonitorPolicy;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-03 and RN-03 in the domain: when a patient is overdue and how it is escalated.
 */
class PatientEscalationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant DEADLINE = REGISTERED_AT.plus(Duration.ofMinutes(5));

    @ParameterizedTest(name = "{0} ms after the deadline -> overdue: {1}")
    @CsvSource({"-60000, false", "0, false", "1, true", "600000, true"})
    void shouldBeOverdueOnlyAfterItsSlaPassed(long millisAfterDeadline, boolean overdue) {
        var now = DEADLINE.plusMillis(millisAfterDeadline);

        assertThat(critical(PatientStatus.REGISTERED, false).isOverdue(now)).isEqualTo(overdue);
    }

    @Test
    void shouldNeverBeOverdueInConsultationDischargedOrAlreadyEscalated() {
        var muchLater = DEADLINE.plus(Duration.ofHours(3));

        assertThat(critical(PatientStatus.IN_CONSULTATION, false).isOverdue(muchLater)).isFalse();
        assertThat(critical(PatientStatus.DISCHARGED, false).isOverdue(muchLater)).isFalse();
        assertThat(critical(PatientStatus.REGISTERED, true).isOverdue(muchLater)).isFalse();
    }

    @Test
    void shouldEscalateKeepingTheStatusAndAuditTheFlagChange() {
        var at = DEADLINE.plusSeconds(30);
        var waiting = critical(PatientStatus.REGISTERED, false);

        var change = PatientChange.escalation(waiting, at);

        assertThat(change.patient().isEscalated()).isTrue();
        assertThat(change.patient().getStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(change.patient().getStatusChangedAt()).isEqualTo(REGISTERED_AT);
        assertThat(change.previousStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(change.isRegistration()).isFalse();
        assertThat(change.auditEntry().eventType()).isEqualTo(PatientEventType.ESCALATED);
        assertThat(change.auditEntry().previousStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(change.auditEntry().newStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(change.auditEntry().occurredAt()).isEqualTo(at);
        assertThat(waiting.isEscalated()).as("the original instance does not change").isFalse();
    }

    @ParameterizedTest(name = "{0} cannot be escalated")
    @ValueSource(strings = {"IN_CONSULTATION", "DISCHARGED"})
    void shouldNotEscalateAPatientThatIsNotWaiting(PatientStatus status) {
        var patient = critical(status, false);

        assertThatThrownBy(patient::escalate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotEscalateTwice() {
        var escalated = critical(PatientStatus.REGISTERED, true);

        assertThatThrownBy(() -> PatientChange.escalation(escalated, DEADLINE))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "batch size {0} is rejected")
    @ValueSource(ints = {0, -3})
    void shouldRejectANonPositiveMonitorBatch(int batchSize) {
        assertThatThrownBy(() -> new SlaMonitorPolicy(batchSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(batchSize));
    }

    @Test
    void shouldKeepAValidMonitorBatch() {
        assertThat(new SlaMonitorPolicy(1).batchSize()).isEqualTo(1);
    }

    private static Patient critical(PatientStatus status, boolean escalated) {
        return Patient.builder()
                .id(UUID.randomUUID())
                .triageLevel(TriageLevel.CRITICAL)
                .slaMinutes(5)
                .status(status)
                .escalated(escalated)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
    }
}
