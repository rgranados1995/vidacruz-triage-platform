package com.vidacruz.triage.api.patient;

import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.api.patient.dto.ChangeStatusRequest;
import com.vidacruz.triage.api.patient.dto.RegisterPatientRequest;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.usecase.changepatientstatus.ChangePatientStatusUseCase;
import com.vidacruz.triage.usecase.getpatient.GetPatientUseCase;
import com.vidacruz.triage.usecase.getpatient.PatientDetail;
import com.vidacruz.triage.usecase.registerpatient.RegisterPatientUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * HTTP side of RF-01 and RF-02: reads the request, calls the use case and writes the response. Errors are signals
 * handled by the global error handler. Logs carry only ids and codes, never personal or clinical data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientHandler {

    static final String ID_VARIABLE = "id";

    private final RegisterPatientUseCase registerPatient;
    private final ChangePatientStatusUseCase changePatientStatus;
    private final GetPatientUseCase getPatient;

    public Mono<ServerResponse> register(ServerRequest request) {
        return requiredBody(request, RegisterPatientRequest.class)
                .map(PatientMapper::toIntake)
                .flatMap(registerPatient::register)
                .doOnNext(patient -> logChange("Patient registered", patient))
                .flatMap(patient -> ServerResponse
                        .created(request.uriBuilder().path("/{id}").build(patient.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(PatientMapper.toRegistered(patient)));
    }

    public Mono<ServerResponse> changeStatus(ServerRequest request) {
        return patientId(request)
                .zipWith(requiredBody(request, ChangeStatusRequest.class)
                        .map(body -> ContractValues.statusFrom(body.status())))
                .flatMap(target -> changePatientStatus.changeStatus(target.getT1(), target.getT2()))
                .doOnNext(patient -> logChange("Patient status changed", patient))
                .map(patient -> new PatientDetail(patient, Duration.ZERO))
                .flatMap(PatientHandler::detail);
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        return patientId(request)
                .flatMap(getPatient::getPatient)
                .flatMap(PatientHandler::detail);
    }

    private static Mono<ServerResponse> detail(PatientDetail detail) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(PatientMapper.toDetail(detail));
    }

    private static <T> Mono<T> requiredBody(ServerRequest request, Class<T> type) {
        return request.bodyToMono(type)
                .switchIfEmpty(Mono.defer(() -> Mono.error(
                        new InvalidRequestException(null, "El cuerpo de la solicitud es obligatorio"))));
    }

    private static Mono<UUID> patientId(ServerRequest request) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable(ID_VARIABLE)))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new InvalidRequestException(ID_VARIABLE, "El id del paciente debe ser un UUID"));
    }

    private static void logChange(String message, Patient patient) {
        log.atInfo()
                .addKeyValue("patientId", patient.getId())
                .addKeyValue("triageLevel", patient.getTriageLevel())
                .addKeyValue("status", patient.getStatus())
                .log(message);
    }
}
