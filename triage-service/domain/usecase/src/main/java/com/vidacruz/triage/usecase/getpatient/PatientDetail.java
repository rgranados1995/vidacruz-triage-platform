package com.vidacruz.triage.usecase.getpatient;

import com.vidacruz.triage.model.patient.Patient;

import java.time.Duration;

/**
 * A patient as of the moment of the query, with how long it has been in its current status.
 */
public record PatientDetail(Patient patient, Duration timeInCurrentStatus) {
}
