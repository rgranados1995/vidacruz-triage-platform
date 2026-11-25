package com.vidacruz.triage.model.validation;

import com.vidacruz.triage.model.exception.InvalidPatientDataException;
import com.vidacruz.triage.model.patient.PatientIntake;
import com.vidacruz.triage.model.patient.VitalSigns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Data ranges of RF-01. Collects every violation so the staff can correct the whole form at once. The chief
 * complaint has no range in RF-01; its maximum length is a defensive limit of this service.
 */
public class PatientDataValidator {

    private static final IntRange AGE = new IntRange(PatientField.AGE, "La edad", 0, 120, "años");
    private static final IntRange HEART_RATE =
            new IntRange(PatientField.HEART_RATE, "La frecuencia cardíaca", 20, 250, "lpm");
    private static final IntRange SYSTOLIC =
            new IntRange(PatientField.SYSTOLIC_PRESSURE, "La presión sistólica", 60, 260, "mmHg");
    private static final IntRange DIASTOLIC =
            new IntRange(PatientField.DIASTOLIC_PRESSURE, "La presión diastólica", 30, 160, "mmHg");
    private static final IntRange OXYGEN = new IntRange(PatientField.OXYGEN_SATURATION, "La SpO2", 70, 100, "%");
    private static final IntRange PAIN = new IntRange(PatientField.PAIN_LEVEL, "El dolor", 0, 10, "");
    private static final IntRange GLASGOW = new IntRange(PatientField.GLASGOW_COMA_SCALE, "El GCS", 3, 15, "");
    private static final BigDecimal TEMPERATURE_MIN = new BigDecimal("34.0");
    private static final BigDecimal TEMPERATURE_MAX = new BigDecimal("42.0");
    private static final int NAME_MIN_LENGTH = 3;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int COMPLAINT_MAX_LENGTH = 500;

    /**
     * Returns the intake when it is valid.
     *
     * @throws InvalidPatientDataException with every violation when it is not
     */
    public PatientIntake requireValid(PatientIntake intake) {
        var violations = validate(intake);
        if (!violations.isEmpty()) {
            throw new InvalidPatientDataException(violations);
        }
        return intake;
    }

    public List<Violation> validate(PatientIntake intake) {
        var violations = new ArrayList<Violation>();
        checkFullName(intake.fullName(), violations);
        AGE.check(intake.age(), violations);
        checkChiefComplaint(intake.chiefComplaint(), violations);
        checkVitalSigns(intake.vitalSigns() == null ? VitalSigns.builder().build() : intake.vitalSigns(), violations);
        return violations;
    }

    private static void checkVitalSigns(VitalSigns signs, List<Violation> violations) {
        HEART_RATE.check(signs.heartRate(), violations);
        SYSTOLIC.check(signs.systolicPressure(), violations);
        DIASTOLIC.check(signs.diastolicPressure(), violations);
        checkDiastolicBelowSystolic(signs, violations);
        OXYGEN.check(signs.oxygenSaturation(), violations);
        checkTemperature(signs.temperature(), violations);
        PAIN.check(signs.painLevel(), violations);
        GLASGOW.check(signs.glasgowComaScale(), violations);
    }

    private static void checkFullName(String fullName, List<Violation> violations) {
        var length = fullName == null ? 0 : fullName.trim().length();
        if (length < NAME_MIN_LENGTH || length > NAME_MAX_LENGTH) {
            violations.add(new Violation(PatientField.FULL_NAME, "El nombre completo debe tener entre "
                    + NAME_MIN_LENGTH + " y " + NAME_MAX_LENGTH + " caracteres"));
        }
    }

    private static void checkChiefComplaint(String complaint, List<Violation> violations) {
        if (complaint == null || complaint.isBlank()) {
            violations.add(new Violation(PatientField.CHIEF_COMPLAINT, "Falta la queja principal"));
        } else if (complaint.length() > COMPLAINT_MAX_LENGTH) {
            violations.add(new Violation(PatientField.CHIEF_COMPLAINT,
                    "La queja principal no puede superar " + COMPLAINT_MAX_LENGTH + " caracteres"));
        }
    }

    private static void checkDiastolicBelowSystolic(VitalSigns signs, List<Violation> violations) {
        var systolic = signs.systolicPressure();
        var diastolic = signs.diastolicPressure();
        if (systolic != null && diastolic != null && diastolic >= systolic) {
            violations.add(new Violation(PatientField.DIASTOLIC_PRESSURE,
                    "La presión diastólica debe ser menor que la sistólica"));
        }
    }

    private static void checkTemperature(BigDecimal temperature, List<Violation> violations) {
        if (temperature == null) {
            violations.add(new Violation(PatientField.TEMPERATURE, "Falta la temperatura"));
        } else if (temperature.compareTo(TEMPERATURE_MIN) < 0 || temperature.compareTo(TEMPERATURE_MAX) > 0) {
            violations.add(new Violation(PatientField.TEMPERATURE,
                    "La temperatura debe estar entre " + TEMPERATURE_MIN + " y " + TEMPERATURE_MAX + " °C"));
        }
    }

    private record IntRange(PatientField field, String label, int min, int max, String unit) {

        void check(Integer value, List<Violation> violations) {
            if (value == null) {
                var article = Character.toLowerCase(label.charAt(0)) + label.substring(1);
                violations.add(new Violation(field, "Falta " + article));
            } else if (value < min || value > max) {
                var suffix = unit.isEmpty() ? "" : " " + unit;
                violations.add(new Violation(field, label + " debe estar entre " + min + " y " + max + suffix));
            }
        }
    }
}
