package com.vidacruz.triage.model.validation;

/**
 * Fields of the patient intake that can fail validation. The entry point maps each one to the name of the field in
 * its own contract.
 */
public enum PatientField {
    FULL_NAME,
    AGE,
    HEART_RATE,
    SYSTOLIC_PRESSURE,
    DIASTOLIC_PRESSURE,
    OXYGEN_SATURATION,
    TEMPERATURE,
    PAIN_LEVEL,
    GLASGOW_COMA_SCALE,
    CHIEF_COMPLAINT
}
