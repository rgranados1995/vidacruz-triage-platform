package com.vidacruz.triage.usecase.changepatientstatus;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.gateways.PatientRepository;
import com.vidacruz.triage.usecase.getpatient.PatientDetail;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

/**
 * RF-02: moves a patient through the clinical flow and audits the change (RN-04).
 */
@RequiredArgsConstructor
public class ChangePatientStatusUseCase {

    private final PatientRepository patientRepository;
    private final Clock clock;

    /**
     * Emits the patient in its new status, with its time in that status like the query of RF-02, or a
     * {@link BusinessException} with {@link BusinessError#PATIENT_NOT_FOUND} or
     * {@link BusinessError#INVALID_STATUS_TRANSITION}.
     */
    public Mono<PatientDetail> changeStatus(UUID patientId, PatientStatus next) {
        return patientRepository.findById(patientId)
                .switchIfEmpty(Mono.defer(() -> Mono.error(new BusinessException(BusinessError.PATIENT_NOT_FOUND))))
                .map(patient -> PatientChange.transition(patient, next, clock.instant()))
                .flatMap(patientRepository::update)
                .map(stored -> new PatientDetail(stored, stored.timeInCurrentStatus(clock.instant())));
    }
}
