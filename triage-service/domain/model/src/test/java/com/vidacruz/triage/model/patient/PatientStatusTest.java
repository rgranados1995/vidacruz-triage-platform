package com.vidacruz.triage.model.patient;

import com.vidacruz.triage.model.audit.PatientEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PatientStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "REGISTERED, REGISTERED, false",
            "REGISTERED, IN_CONSULTATION, true",
            "REGISTERED, DISCHARGED, true",
            "IN_CONSULTATION, REGISTERED, false",
            "IN_CONSULTATION, IN_CONSULTATION, false",
            "IN_CONSULTATION, DISCHARGED, true",
            "DISCHARGED, REGISTERED, false",
            "DISCHARGED, IN_CONSULTATION, false",
            "DISCHARGED, DISCHARGED, false"})
    void shouldAllowOnlyTheTransitionsOfTheClinicalFlow(PatientStatus from, PatientStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void shouldConsiderActiveEveryPatientNotDischarged() {
        assertThat(PatientStatus.REGISTERED.isActive()).isTrue();
        assertThat(PatientStatus.IN_CONSULTATION.isActive()).isTrue();
        assertThat(PatientStatus.DISCHARGED.isActive()).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"REGISTERED, REGISTERED", "IN_CONSULTATION, CONSULTATION_STARTED", "DISCHARGED, DISCHARGED"})
    void shouldNameTheEventOfEnteringEachStatus(PatientStatus status, PatientEventType expected) {
        assertThat(PatientEventType.ofTransitionTo(status)).isEqualTo(expected);
    }
}
