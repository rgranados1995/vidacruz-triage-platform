package com.vidacruz.triage.model.validation;

/**
 * One invalid field of the patient intake, with the message shown to the clinical staff.
 */
public record Violation(PatientField field, String message) {
}
