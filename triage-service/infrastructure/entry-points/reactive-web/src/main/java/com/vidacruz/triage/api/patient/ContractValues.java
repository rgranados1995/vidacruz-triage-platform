package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.contract.ContractCodes;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.validation.PatientField;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Values that only the HTTP input needs: reading a status code of the request and naming the fields of the request
 * body. The codes published by the API and the events live in {@link ContractCodes}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContractValues {

    public static final String STATUS_FIELD = "estado";

    /**
     * @throws InvalidRequestException when the code is missing or is not a status of the contract
     */
    public static PatientStatus statusFrom(String code) {
        return ContractCodes.statusOf(code).orElseThrow(() -> new InvalidRequestException(STATUS_FIELD,
                "El estado debe ser REGISTRADO, EN_CONSULTA o ALTA"));
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
