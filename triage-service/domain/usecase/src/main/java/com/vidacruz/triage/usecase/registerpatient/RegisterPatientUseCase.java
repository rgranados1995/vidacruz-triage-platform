package com.vidacruz.triage.usecase.registerpatient;

import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientIntake;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.model.triage.SlaPolicy;
import com.vidacruz.triage.model.triage.TriageClassifier;
import com.vidacruz.triage.model.validation.PatientDataValidator;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

/**
 * RF-01: validates the intake, classifies the patient (RN-01), fixes its SLA (RN-02) and stores it with the
 * registration audit entry (RN-04).
 */
@RequiredArgsConstructor
public class RegisterPatientUseCase {

    private final PatientRepository patientRepository;
    private final PatientDataValidator validator;
    private final TriageClassifier classifier;
    private final SlaPolicy slaPolicy;
    private final Clock clock;

    /**
     * Emits the registered patient, or an {@link com.vidacruz.triage.model.exception.InvalidPatientDataException}
     * with every invalid field.
     */
    public Mono<Patient> register(PatientIntake intake) {
        return Mono.fromCallable(() -> validator.requireValid(intake))
                .map(this::admit)
                .flatMap(patient -> patientRepository.register(PatientChange.registration(patient)));
    }

    private Patient admit(PatientIntake intake) {
        var level = classifier.classify(intake.vitalSigns(), intake.chiefComplaint());
        var now = clock.instant();
        return Patient.builder()
                .id(UUID.randomUUID())
                .fullName(intake.fullName().trim())
                .age(intake.age())
                .vitalSigns(intake.vitalSigns())
                .chiefComplaint(intake.chiefComplaint().trim())
                .triageLevel(level)
                .slaMinutes(slaPolicy.minutesFor(level))
                .status(PatientStatus.REGISTERED)
                .escalated(false)
                .registeredAt(now)
                .statusChangedAt(now)
                .build();
    }
}
