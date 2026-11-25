package com.vidacruz.triage.model.exception;

import com.vidacruz.triage.model.validation.Violation;
import lombok.Getter;

import java.util.List;

/**
 * The patient intake breaks one or more data ranges of RF-01. It carries every violation, not only the first one,
 * so the staff can fix the whole form at once.
 */
@Getter
public class InvalidPatientDataException extends BusinessException {

    private final transient List<Violation> violations;

    public InvalidPatientDataException(List<Violation> violations) {
        super(BusinessError.INVALID_PATIENT_DATA, violations.size() + " invalid field(s)");
        this.violations = List.copyOf(violations);
    }
}
