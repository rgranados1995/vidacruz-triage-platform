package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.error.ApiError;
import com.vidacruz.triage.api.patient.dto.ChangeStatusRequest;
import com.vidacruz.triage.api.patient.dto.PatientDetailResponse;
import com.vidacruz.triage.api.patient.dto.RegisterPatientRequest;
import com.vidacruz.triage.api.patient.dto.RegisteredPatientResponse;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springdoc.core.fn.builders.operation.Builder;

import java.util.UUID;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

/**
 * OpenAPI documentation of the patient routes, written with the springdoc DSL next to the routes. Every operation
 * lists its error responses; the examples come from the {@code @Schema} of each DTO.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PatientApiDocs {

    private static final String TAG = "Pacientes";

    static void register(Builder operation) {
        operation.operationId("registrarPaciente").tag(TAG)
                .summary("Registra un paciente y lo clasifica (RF-01)")
                .description("Valida los rangos, clasifica por RN-01, asigna el SLA de RN-02 y deja la auditoría.")
                .requestBody(requestBodyBuilder().required(true).implementation(RegisterPatientRequest.class))
                .response(responseBuilder().responseCode("201").description("Paciente registrado; Location apunta "
                        + "a su detalle").implementation(RegisteredPatientResponse.class))
                .response(error("400", "Algún campo está fuera de rango, falta o tiene un formato inválido"))
                .response(error("401", "Falta la API key o no es válida"))
                .response(error("500", "Error inesperado"));
    }

    static void changeStatus(Builder operation) {
        operation.operationId("cambiarEstadoPaciente").tag(TAG)
                .summary("Cambia el estado clínico del paciente (RF-02)")
                .description("Transiciones válidas: REGISTRADO → EN_CONSULTA, REGISTRADO → ALTA, EN_CONSULTA → ALTA.")
                .parameter(patientId())
                .requestBody(requestBodyBuilder().required(true).implementation(ChangeStatusRequest.class))
                .response(responseBuilder().responseCode("200").description("Paciente en su nuevo estado")
                        .implementation(PatientDetailResponse.class))
                .response(error("400", "Estado desconocido o id que no es UUID"))
                .response(error("401", "Falta la API key o no es válida"))
                .response(error("404", "El paciente no existe"))
                .response(error("409", "Transición no permitida por el flujo clínico"))
                .response(error("500", "Error inesperado"));
    }

    static void get(Builder operation) {
        operation.operationId("consultarPaciente").tag(TAG)
                .summary("Consulta un paciente (RF-02)")
                .description("Datos completos, estado actual, flags y tiempo en el estado actual.")
                .parameter(patientId())
                .response(responseBuilder().responseCode("200").description("Detalle del paciente")
                        .implementation(PatientDetailResponse.class))
                .response(error("400", "El id no es un UUID"))
                .response(error("401", "Falta la API key o no es válida"))
                .response(error("404", "El paciente no existe"))
                .response(error("500", "Error inesperado"));
    }

    private static org.springdoc.core.fn.builders.parameter.Builder patientId() {
        return parameterBuilder().in(ParameterIn.PATH).name(PatientHandler.ID_VARIABLE).required(true)
                .description("Id del paciente").implementation(UUID.class);
    }

    private static org.springdoc.core.fn.builders.apiresponse.Builder error(String status, String description) {
        return responseBuilder().responseCode(status).description(description).implementation(ApiError.class);
    }
}
