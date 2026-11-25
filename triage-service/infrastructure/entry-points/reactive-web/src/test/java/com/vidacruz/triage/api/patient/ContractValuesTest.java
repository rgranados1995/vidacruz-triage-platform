package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.validation.PatientField;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractValuesTest {

    @ParameterizedTest(name = "{1} -> {0}")
    @CsvSource({"REGISTERED, REGISTRADO", "IN_CONSULTATION, EN_CONSULTA", "DISCHARGED, ALTA"})
    void shouldReadEachStatusOfTheContract(PatientStatus status, String code) {
        assertThat(ContractValues.statusFrom(code)).isEqualTo(status);
    }

    @ParameterizedTest(name = "\"{0}\" is not a status")
    @NullAndEmptySource
    @ValueSource(strings = {"en_consulta", "OBSERVACION", "IN_CONSULTATION"})
    void shouldRejectAStatusOutsideTheContract(String code) {
        assertThatThrownBy(() -> ContractValues.statusFrom(code))
                .isInstanceOfSatisfying(InvalidRequestException.class, error -> {
                    assertThat(error.getField()).isEqualTo("estado");
                    assertThat(error.getReason()).isEqualTo("El estado debe ser REGISTRADO, EN_CONSULTA o ALTA");
                });
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
