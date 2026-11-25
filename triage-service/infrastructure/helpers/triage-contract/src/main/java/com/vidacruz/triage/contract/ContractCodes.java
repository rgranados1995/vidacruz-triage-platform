package com.vidacruz.triage.contract;

import com.vidacruz.triage.model.audit.PatientEventType;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.triage.TriageLevel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Codes of the statement (Spanish) for the domain enums (English), shared by the HTTP API and the events so both
 * always publish the same values. Every mapping is explicit: a rename in the domain cannot change the contract.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContractCodes {

    public static String levelCode(TriageLevel level) {
        return switch (level) {
            case CRITICAL -> "CRITICO";
            case URGENT -> "URGENTE";
            case URGENT_2 -> "URGENTE_2";
            case NON_URGENT -> "NO_URGENTE";
            case NON_URGENT_2 -> "NO_URGENTE_2";
        };
    }

    public static String statusCode(PatientStatus status) {
        return switch (status) {
            case REGISTERED -> "REGISTRADO";
            case IN_CONSULTATION -> "EN_CONSULTA";
            case DISCHARGED -> "ALTA";
        };
    }

    /** Status for a code of the contract; empty when the code is missing or unknown. */
    public static Optional<PatientStatus> statusOf(String code) {
        return Optional.ofNullable(switch (code == null ? "" : code) {
            case "REGISTRADO" -> PatientStatus.REGISTERED;
            case "EN_CONSULTA" -> PatientStatus.IN_CONSULTATION;
            case "ALTA" -> PatientStatus.DISCHARGED;
            default -> null;
        });
    }

    /** Event names of the topic {@code triaje.eventos}. */
    public static String eventType(PatientEventType type) {
        return switch (type) {
            case REGISTERED -> "paciente_registrado";
            case CONSULTATION_STARTED -> "paciente_en_consulta";
            case DISCHARGED -> "paciente_alta";
            case ESCALATED -> "paciente_escalado";
        };
    }
}
