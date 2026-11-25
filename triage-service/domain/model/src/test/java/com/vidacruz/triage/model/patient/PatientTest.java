package com.vidacruz.triage.model.patient;

import com.vidacruz.triage.model.audit.PatientEventType;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plus(Duration.ofMinutes(7));

    private final Patient registered = Patient.builder()
            .id(UUID.randomUUID())
            .triageLevel(TriageLevel.URGENT)
            .slaMinutes(10)
            .status(PatientStatus.REGISTERED)
            .registeredAt(REGISTERED_AT)
            .statusChangedAt(REGISTERED_AT)
            .build();

    @Test
    void shouldRecordTheFirstEntryToTheConsultation() {
        var inConsultation = registered.moveTo(PatientStatus.IN_CONSULTATION, LATER);

        assertThat(inConsultation.getStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
        assertThat(inConsultation.getStatusChangedAt()).isEqualTo(LATER);
        assertThat(inConsultation.getConsultationStartedAt()).isEqualTo(LATER);
        assertThat(registered.getStatus()).as("the original instance does not change")
                .isEqualTo(PatientStatus.REGISTERED);
    }

    @Test
    void shouldDischargeDirectlyWithoutAConsultationTime() {
        var discharged = registered.moveTo(PatientStatus.DISCHARGED, LATER);

        assertThat(discharged.getStatus()).isEqualTo(PatientStatus.DISCHARGED);
        assertThat(discharged.getConsultationStartedAt()).isNull();
    }

    @Test
    void shouldKeepTheConsultationTimeWhenDischargedAfterTheConsultation() {
        var discharged = registered.moveTo(PatientStatus.IN_CONSULTATION, LATER)
                .moveTo(PatientStatus.DISCHARGED, LATER.plusSeconds(600));

        assertThat(discharged.getConsultationStartedAt()).isEqualTo(LATER);
        assertThat(discharged.getStatusChangedAt()).isEqualTo(LATER.plusSeconds(600));
    }

    @Test
    void shouldRejectATransitionOutsideTheClinicalFlowWithADescriptiveDetail() {
        var discharged = registered.moveTo(PatientStatus.DISCHARGED, LATER);

        assertThatThrownBy(() -> discharged.moveTo(PatientStatus.IN_CONSULTATION, LATER))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getError()).isEqualTo(BusinessError.INVALID_STATUS_TRANSITION);
                    assertThat(error.getDetail()).isEqualTo("de \"alta\" a \"en consulta\"");
                    assertThat(error.getMessage())
                            .isEqualTo("TRG-003 Transición de estado no permitida: de \"alta\" a \"en consulta\"");
                });
    }

    @Test
    void shouldMeasureTheTimeInTheCurrentStatus() {
        assertThat(registered.timeInCurrentStatus(LATER)).isEqualTo(Duration.ofMinutes(7));
    }

    @Test
    void shouldAuditTheRegistrationWithoutPreviousStatus() {
        var change = PatientChange.registration(registered);

        assertThat(change.isRegistration()).isTrue();
        assertThat(change.patient()).isSameAs(registered);
        assertThat(change.auditEntry().patientId()).isEqualTo(registered.getId());
        assertThat(change.auditEntry().eventType()).isEqualTo(PatientEventType.REGISTERED);
        assertThat(change.auditEntry().previousStatus()).isNull();
        assertThat(change.auditEntry().newStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(change.auditEntry().occurredAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void shouldAuditATransitionWithTheStatusBeforeAndAfter() {
        var change = PatientChange.transition(registered, PatientStatus.IN_CONSULTATION, LATER);

        assertThat(change.isRegistration()).isFalse();
        assertThat(change.previousStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(change.patient().getStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
        assertThat(change.auditEntry().eventType()).isEqualTo(PatientEventType.CONSULTATION_STARTED);
        assertThat(change.auditEntry().previousStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(change.auditEntry().newStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
        assertThat(change.auditEntry().occurredAt()).isEqualTo(LATER);
    }

    @Test
    void shouldDescribeABusinessErrorWithoutDetailByItsCodeAndMessage() {
        var error = new BusinessException(BusinessError.PATIENT_NOT_FOUND);

        assertThat(error.getMessage()).isEqualTo("TRG-002 No existe un paciente con el id indicado");
        assertThat(error.getDetail()).isEmpty();
    }
}
