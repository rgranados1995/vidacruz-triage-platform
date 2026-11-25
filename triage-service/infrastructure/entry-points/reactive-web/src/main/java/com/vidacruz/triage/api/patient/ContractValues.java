package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.model.validation.PatientField;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Values of the HTTP contract of the statement (Spanish codes) for the domain enums, which are in English. Every
 * mapping is explicit so a rename in the domain can never change the contract silently.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContractValues {

    public static final String STATUS_FIELD = "estado";

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

    /**
     * @throws InvalidRequestException when the code is missing or is not a status of the contract
     */
    public static PatientStatus statusFrom(String code) {
        return switch (Objects.requireNonNullElse(code, "")) {
            case "REGISTRADO" -> PatientStatus.REGISTERED;
            case "EN_CONSULTA" -> PatientStatus.IN_CONSULTATION;
            case "ALTA" -> PatientStatus.DISCHARGED;
            default -> throw new InvalidRequestException(STATUS_FIELD,
                    "El estado debe ser REGISTRADO, EN_CONSULTA o ALTA");
        };
    }

    /** Name of the field in the request body. */
    public static String fieldName(PatientField field) {
        return switch (field) {
            case FULL_NAME -> "nombreCompleto";
            case AGE -> "edad";
            case HEART_RATE -> "frecuenciaCardiaca";
            case SYSTOLIC_PRESSURE -> "presionSistolica";
            case DIASTOLIC_PRESSURE -> "presionDiastolica";
            case OXYGEN_SATURATION -> "spo2";
            case TEMPERATURE -> "temperatura";
            case PAIN_LEVEL -> "dolor";
            case GLASGOW_COMA_SCALE -> "gcs";
            case CHIEF_COMPLAINT -> "quejaPrincipal";
        };
    }
}
