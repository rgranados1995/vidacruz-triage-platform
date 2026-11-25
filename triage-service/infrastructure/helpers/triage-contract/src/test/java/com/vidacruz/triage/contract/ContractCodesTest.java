package com.vidacruz.triage.contract;

import com.vidacruz.triage.model.audit.PatientEventType;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.triage.TriageLevel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The values of the contract are the ones of the statement; a rename in the domain must not change them.
 */
class ContractCodesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"CRITICAL, CRITICO", "URGENT, URGENTE", "URGENT_2, URGENTE_2", "NON_URGENT, NO_URGENTE",
            "NON_URGENT_2, NO_URGENTE_2"})
    void shouldPublishEachLevelWithTheCodeOfTheStatement(TriageLevel level, String code) {
        assertThat(ContractCodes.levelCode(level)).isEqualTo(code);
    }

    @ParameterizedTest(name = "{0} <-> {1}")
    @CsvSource({"REGISTERED, REGISTRADO", "IN_CONSULTATION, EN_CONSULTA", "DISCHARGED, ALTA"})
    void shouldMapEachStatusBothWays(PatientStatus status, String code) {
        assertThat(ContractCodes.statusCode(status)).isEqualTo(code);
        assertThat(ContractCodes.statusOf(code)).contains(status);
    }

    @ParameterizedTest(name = "\"{0}\" is not a status")
    @NullAndEmptySource
    @ValueSource(strings = {"en_consulta", "OBSERVACION", "IN_CONSULTATION"})
    void shouldNotRecognizeAStatusOutsideTheContract(String code) {
        assertThat(ContractCodes.statusOf(code)).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"REGISTERED, paciente_registrado", "CONSULTATION_STARTED, paciente_en_consulta",
            "DISCHARGED, paciente_alta", "ESCALATED, paciente_escalado"})
    void shouldNameEachEventAsTheStatement(PatientEventType type, String name) {
        assertThat(ContractCodes.eventType(type)).isEqualTo(name);
    }
}
