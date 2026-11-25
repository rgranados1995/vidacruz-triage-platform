package com.vidacruz.triage.model.patient;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.triage.TriageLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A patient in the emergency room, from the registration until the discharge. Immutable: every change returns a new
 * instance, so a use case can keep the state before and after the change for the clinical audit.
 */
@Getter
@Builder(toBuilder = true)
public class Patient {

    private final UUID id;
    private final String fullName;
    private final Integer age;
    private final VitalSigns vitalSigns;
    private final String chiefComplaint;
    private final TriageLevel triageLevel;
    /** SLA promised at the registration; later changes of the SLA configuration do not alter it. */
    private final Integer slaMinutes;
    private final PatientStatus status;
    /** Persistent flag of RF-03: set once when the patient waited longer than the SLA. */
    private final boolean escalated;
    private final Instant registeredAt;
    private final Instant statusChangedAt;
    /** First entry to the consultation; null while the patient has not been seen. */
    private final Instant consultationStartedAt;

    /**
     * Moves the patient to the next status of the clinical flow.
     *
     * @throws BusinessException with {@link BusinessError#INVALID_STATUS_TRANSITION} when RF-02 does not allow it
     */
    public Patient moveTo(PatientStatus next, Instant at) {
        if (!status.canMoveTo(next)) {
            throw new BusinessException(BusinessError.INVALID_STATUS_TRANSITION,
                    "de \"" + status.getDescription() + "\" a \"" + next.getDescription() + "\"");
        }
        var moved = toBuilder().status(next).statusChangedAt(at);
        if (next == PatientStatus.IN_CONSULTATION) {
            moved.consultationStartedAt(at);
        }
        return moved.build();
    }

    public Duration timeInCurrentStatus(Instant now) {
        return Duration.between(statusChangedAt, now);
    }
}
