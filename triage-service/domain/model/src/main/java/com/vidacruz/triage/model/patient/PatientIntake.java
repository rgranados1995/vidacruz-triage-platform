package com.vidacruz.triage.model.patient;

import lombok.Builder;

/**
 * Data captured at the reception to register a patient, before validation and classification.
 */
@Builder(toBuilder = true)
public record PatientIntake(String fullName, Integer age, VitalSigns vitalSigns, String chiefComplaint) {
}
