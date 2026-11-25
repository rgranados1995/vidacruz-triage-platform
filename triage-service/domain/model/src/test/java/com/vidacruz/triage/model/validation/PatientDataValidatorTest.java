package com.vidacruz.triage.model.validation;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.InvalidPatientDataException;
import com.vidacruz.triage.model.patient.PatientIntake;
import com.vidacruz.triage.model.patient.VitalSigns;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientDataValidatorTest {

    /** Patient E1 of the statement: every field inside its range. */
    private static final PatientIntake VALID = PatientIntake.builder()
            .fullName("Ana María Pérez")
            .age(54)
            .chiefComplaint("Dolor torácico")
            .vitalSigns(VitalSigns.builder()
                    .heartRate(130).systolicPressure(180).diastolicPressure(110).oxygenSaturation(97)
                    .temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15)
                    .build())
            .build();

    private final PatientDataValidator validator = new PatientDataValidator();

    @Test
    void shouldAcceptAnIntakeInsideEveryRange() {
        assertThat(validator.validate(VALID)).isEmpty();
        assertThat(validator.requireValid(VALID)).isSameAs(VALID);
    }

    @ParameterizedTest(name = "{0} = {1} valid: {2}")
    @CsvSource({
            "AGE, -1, false", "AGE, 0, true", "AGE, 120, true", "AGE, 121, false",
            "HEART_RATE, 19, false", "HEART_RATE, 20, true", "HEART_RATE, 250, true", "HEART_RATE, 251, false",
            "SYSTOLIC_PRESSURE, 59, false", "SYSTOLIC_PRESSURE, 60, true", "SYSTOLIC_PRESSURE, 260, true",
            "SYSTOLIC_PRESSURE, 261, false",
            "DIASTOLIC_PRESSURE, 29, false", "DIASTOLIC_PRESSURE, 30, true", "DIASTOLIC_PRESSURE, 160, true",
            "DIASTOLIC_PRESSURE, 161, false",
            "OXYGEN_SATURATION, 69, false", "OXYGEN_SATURATION, 70, true", "OXYGEN_SATURATION, 100, true",
            "OXYGEN_SATURATION, 101, false",
            "PAIN_LEVEL, -1, false", "PAIN_LEVEL, 0, true", "PAIN_LEVEL, 10, true", "PAIN_LEVEL, 11, false",
            "GLASGOW_COMA_SCALE, 2, false", "GLASGOW_COMA_SCALE, 3, true", "GLASGOW_COMA_SCALE, 15, true",
            "GLASGOW_COMA_SCALE, 16, false"})
    void shouldApplyTheRangeOfEachNumericField(PatientField field, int value, boolean valid) {
        var violations = validator.validate(withValue(field, value));

        assertThat(fieldsOf(violations).contains(field)).isNotEqualTo(valid);
    }

    @ParameterizedTest(name = "temperature {0} valid: {1}")
    @CsvSource({"33.9, false", "34.0, true", "42.0, true", "42.1, false"})
    void shouldApplyTheTemperatureRange(String temperature, boolean valid) {
        var intake = VALID.toBuilder()
                .vitalSigns(VALID.vitalSigns().toBuilder().temperature(new BigDecimal(temperature)).build())
                .build();

        assertThat(fieldsOf(validator.validate(intake)).contains(PatientField.TEMPERATURE))
                .isNotEqualTo(valid);
    }

    @ParameterizedTest(name = "\"{0}\" is not a valid name")
    @NullAndEmptySource
    @ValueSource(strings = {"Al", "   Al   "})
    void shouldRejectANameShorterThanThreeCharacters(String fullName) {
        var violations = validator.validate(VALID.toBuilder().fullName(fullName).build());

        assertThat(violations).containsExactly(new Violation(PatientField.FULL_NAME,
                "El nombre completo debe tener entre 3 y 100 caracteres"));
    }

    @Test
    void shouldAcceptNamesFromThreeToOneHundredCharacters() {
        assertThat(validator.validate(VALID.toBuilder().fullName("Ana").build())).isEmpty();
        assertThat(validator.validate(VALID.toBuilder().fullName("a".repeat(100)).build())).isEmpty();
        assertThat(fieldsOf(validator.validate(VALID.toBuilder().fullName("a".repeat(101)).build())))
                .containsExactly(PatientField.FULL_NAME);
    }

    @ParameterizedTest(name = "complaint \"{0}\" is missing")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRequireTheChiefComplaint(String complaint) {
        var violations = validator.validate(VALID.toBuilder().chiefComplaint(complaint).build());

        assertThat(violations)
                .containsExactly(new Violation(PatientField.CHIEF_COMPLAINT, "Falta la queja principal"));
    }

    @Test
    void shouldLimitTheChiefComplaintToFiveHundredCharacters() {
        assertThat(validator.validate(VALID.toBuilder().chiefComplaint("x".repeat(500)).build())).isEmpty();
        assertThat(validator.validate(VALID.toBuilder().chiefComplaint("x".repeat(501)).build()))
                .containsExactly(new Violation(PatientField.CHIEF_COMPLAINT,
                        "La queja principal no puede superar 500 caracteres"));
    }

    @ParameterizedTest(name = "diastolic {0} with systolic 120 valid: {1}")
    @CsvSource({"119, true", "120, false", "121, false"})
    void shouldRequireTheDiastolicPressureBelowTheSystolic(int diastolic, boolean valid) {
        var intake = VALID.toBuilder()
                .vitalSigns(VALID.vitalSigns().toBuilder().systolicPressure(120).diastolicPressure(diastolic).build())
                .build();

        var expected = valid ? List.<Violation>of() : List.of(new Violation(PatientField.DIASTOLIC_PRESSURE,
                "La presión diastólica debe ser menor que la sistólica"));
        assertThat(validator.validate(intake)).isEqualTo(expected);
    }

    @Test
    void shouldExplainEachRangeViolationWithItsLimitsAndUnit() {
        var intake = VALID.toBuilder()
                .age(130)
                .vitalSigns(VALID.vitalSigns().toBuilder()
                        .heartRate(300).oxygenSaturation(50).temperature(new BigDecimal("45")).painLevel(12)
                        .build())
                .build();

        assertThat(validator.validate(intake)).containsExactly(
                new Violation(PatientField.AGE, "La edad debe estar entre 0 y 120 años"),
                new Violation(PatientField.HEART_RATE, "La frecuencia cardíaca debe estar entre 20 y 250 lpm"),
                new Violation(PatientField.OXYGEN_SATURATION, "La SpO2 debe estar entre 70 y 100 %"),
                new Violation(PatientField.TEMPERATURE, "La temperatura debe estar entre 34.0 y 42.0 °C"),
                new Violation(PatientField.PAIN_LEVEL, "El dolor debe estar entre 0 y 10"));
    }

    @Test
    void shouldReportEveryMissingVitalSignWhenTheyAreAbsent() {
        var violations = validator.validate(VALID.toBuilder().vitalSigns(null).build());

        assertThat(violations).containsExactly(
                new Violation(PatientField.HEART_RATE, "Falta la frecuencia cardíaca"),
                new Violation(PatientField.SYSTOLIC_PRESSURE, "Falta la presión sistólica"),
                new Violation(PatientField.DIASTOLIC_PRESSURE, "Falta la presión diastólica"),
                new Violation(PatientField.OXYGEN_SATURATION, "Falta la SpO2"),
                new Violation(PatientField.TEMPERATURE, "Falta la temperatura"),
                new Violation(PatientField.PAIN_LEVEL, "Falta el dolor"),
                new Violation(PatientField.GLASGOW_COMA_SCALE, "Falta el GCS"));
    }

    @Test
    void shouldReportAMissingAge() {
        assertThat(validator.validate(VALID.toBuilder().age(null).build()))
                .containsExactly(new Violation(PatientField.AGE, "Falta la edad"));
    }

    @Test
    void shouldFailWithEveryViolationWhenTheIntakeIsRequiredToBeValid() {
        var intake = VALID.toBuilder().fullName("Al").age(-5).build();

        assertThatThrownBy(() -> validator.requireValid(intake))
                .isInstanceOfSatisfying(InvalidPatientDataException.class, error -> {
                    assertThat(error.getError()).isEqualTo(BusinessError.INVALID_PATIENT_DATA);
                    assertThat(fieldsOf(error.getViolations()))
                            .containsExactly(PatientField.FULL_NAME, PatientField.AGE);
                });
    }

    private static PatientIntake withValue(PatientField field, int value) {
        if (field == PatientField.AGE) {
            return VALID.toBuilder().age(value).build();
        }
        var signs = VALID.vitalSigns().toBuilder().systolicPressure(260);
        switch (field) {
            case HEART_RATE -> signs.heartRate(value);
            case SYSTOLIC_PRESSURE -> signs.systolicPressure(value).diastolicPressure(50);
            case DIASTOLIC_PRESSURE -> signs.diastolicPressure(value);
            case OXYGEN_SATURATION -> signs.oxygenSaturation(value);
            case PAIN_LEVEL -> signs.painLevel(value);
            case GLASGOW_COMA_SCALE -> signs.glasgowComaScale(value);
            default -> throw new IllegalArgumentException("Not a numeric vital sign: " + field);
        }
        return VALID.toBuilder().vitalSigns(signs.build()).build();
    }

    private static List<PatientField> fieldsOf(List<Violation> violations) {
        var fields = new ArrayList<PatientField>();
        for (var violation : violations) {
            fields.add(violation.field());
        }
        return fields;
    }
}
