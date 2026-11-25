package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.model.validation.PatientField;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The values of the contract are the ones of the statement; a rename in the domain must not change them.
 */
class ContractValuesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"CRITICAL, CRITICO", "URGENT, URGENTE", "URGENT_2, URGENTE_2", "NON_URGENT, NO_URGENTE",
            "NON_URGENT_2, NO_URGENTE_2"})
    void shouldPublishEachLevelWithTheCodeOfTheStatement(TriageLevel level, String code) {
        assertThat(ContractValues.levelCode(level)).isEqualTo(code);
    }

    @ParameterizedTest(name = "{0} <-> {1}")
    @CsvSource({"REGISTERED, REGISTRADO", "IN_CONSULTATION, EN_CONSULTA", "DISCHARGED, ALTA"})
    void shouldMapEachStatusBothWays(PatientStatus status, String code) {
        assertThat(ContractValues.statusCode(status)).isEqualTo(code);
        assertThat(ContractValues.statusFrom(code)).isEqualTo(status);
    }

    @ParameterizedTest(name = "\"{0}\" is not a status")
    @NullAndEmptySource
    @ValueSource(strings = {"en_consulta", "OBSERVACION", "IN_CONSULTATION"})
    void shouldRejectAStatusOutsideTheContract(String code) {
        assertThatThrownBy(() -> ContractValues.statusFrom(code))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        error -> assertThat(error.getField()).isEqualTo("estado"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"FULL_NAME, nombreCompleto", "AGE, edad", "HEART_RATE, frecuenciaCardiaca",
            "SYSTOLIC_PRESSURE, presionSistolica", "DIASTOLIC_PRESSURE, presionDiastolica",
            "OXYGEN_SATURATION, spo2", "TEMPERATURE, temperatura", "PAIN_LEVEL, dolor", "GLASGOW_COMA_SCALE, gcs",
            "CHIEF_COMPLAINT, quejaPrincipal"})
    void shouldNameEachFieldAsInTheRequestBody(PatientField field, String name) {
        assertThat(ContractValues.fieldName(field)).isEqualTo(name);
    }
}
