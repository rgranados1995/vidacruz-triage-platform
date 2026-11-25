package com.vidacruz.triage.usecase.getpatient;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

/**
 * RF-02 query: the full patient with its current status, flags and time in that status.
 */
@RequiredArgsConstructor
public class GetPatientUseCase {

    private final PatientRepository patientRepository;
    private final Clock clock;

    /** Emits the patient detail, or a {@link BusinessException} with {@link BusinessError#PATIENT_NOT_FOUND}. */
    public Mono<PatientDetail> getPatient(UUID patientId) {
        return patientRepository.findById(patientId)
                .switchIfEmpty(Mono.defer(() -> Mono.error(new BusinessException(BusinessError.PATIENT_NOT_FOUND))))
                .map(patient -> new PatientDetail(patient, patient.timeInCurrentStatus(clock.instant())));
    }
}
