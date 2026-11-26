package com.vidacruz.triage.api.alert;

import com.vidacruz.triage.api.alert.dto.AcknowledgeAlertRequest;
import com.vidacruz.triage.api.alert.dto.AlertResponse;
import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.usecase.medicalalerts.AcknowledgeAlertUseCase;
import com.vidacruz.triage.usecase.medicalalerts.ListActiveAlertsUseCase;
import com.vidacruz.triage.usecase.medicalalerts.WatchAlertsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * HTTP side of RF-06: the active alerts, their stream (Server-Sent Events) and their acknowledgement. Logs carry only
 * ids and codes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertHandler {

    static final String ID_VARIABLE = "id";
    static final String ALERT_EVENT = "alerta";
    static final String HEARTBEAT_COMMENT = "latido";
    /**
     * Alerts waiting for a slow client. When they are more, its stream is closed: on reconnecting it receives the
     * active alerts again, so it loses nothing and never holds back the other clients.
     */
    static final int STREAM_BUFFER = 256;
    private static final int MIN_RESPONSIBLE = 3;
    private static final int MAX_RESPONSIBLE = 100;
    private static final ParameterizedTypeReference<ServerSentEvent<AlertResponse>> EVENT_TYPE =
            new ParameterizedTypeReference<>() { };

    private final ListActiveAlertsUseCase listActiveAlerts;
    private final WatchAlertsUseCase watchAlerts;
    private final AcknowledgeAlertUseCase acknowledgeAlert;
    private final AlertStreamProperties streamProperties;

    public Mono<ServerResponse> listActive(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(listActiveAlerts.listActive().map(AlertMapper::toResponse), AlertResponse.class);
    }

    /** The active alerts, then every change, with a heartbeat while idle; it ends when the service stops. */
    public Mono<ServerResponse> stream(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(events(), EVENT_TYPE);
    }

    public Mono<ServerResponse> acknowledge(ServerRequest request) {
        return alertId(request)
                .zipWith(responsible(request))
                .flatMap(target -> acknowledgeAlert.acknowledge(target.getT1(), target.getT2()))
                .doOnNext(alert -> logAlert("Alert acknowledged", alert))
                .flatMap(alert -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(AlertMapper.toResponse(alert)));
    }

    Flux<ServerSentEvent<AlertResponse>> events() {
        return Flux.defer(this::eventsOfOneClient)
                .doOnSubscribe(subscription -> log.info("Alert stream opened"))
                .doFinally(signal -> log.atInfo().addKeyValue("signal", signal).log("Alert stream closed"));
    }

    /**
     * A client that does not keep up fills its buffer: the stream then ends at once (an error would wait behind the
     * buffered events) and the client, on reconnecting, receives the active alerts again. Heartbeats count too, so a
     * client that reads nothing at all is also closed after a while.
     */
    private Flux<ServerSentEvent<AlertResponse>> eventsOfOneClient() {
        var heartbeat = ServerSentEvent.<AlertResponse>builder().comment(HEARTBEAT_COMMENT).build();
        var overflow = Sinks.empty();
        return watchAlerts.watch()
                .map(alert -> ServerSentEvent.builder(AlertMapper.toResponse(alert)).event(ALERT_EVENT).build())
                .publish(alerts -> Flux.merge(alerts, Flux.interval(streamProperties.heartbeat())
                        .map(tick -> heartbeat)
                        .takeUntilOther(alerts.then())))
                .onBackpressureBuffer(STREAM_BUFFER, dropped -> overflow.tryEmitEmpty(),
                        BufferOverflowStrategy.DROP_LATEST)
                .takeUntilOther(overflow.asMono()
                        .doOnSuccess(closed -> log.warn("Alert stream client too slow; closing its stream")));
    }

    private static Mono<Long> alertId(ServerRequest request) {
        return Mono.fromCallable(() -> Long.valueOf(request.pathVariable(ID_VARIABLE)))
                .onErrorMap(NumberFormatException.class,
                        error -> new InvalidRequestException(ID_VARIABLE, "El id de la alerta debe ser un número"));
    }

    private static Mono<String> responsible(ServerRequest request) {
        return request.bodyToMono(AcknowledgeAlertRequest.class)
                .mapNotNull(AcknowledgeAlertRequest::responsible)
                .map(String::strip)
                .filter(AlertHandler::isValidResponsible)
                .switchIfEmpty(Mono.defer(() -> Mono.error(new InvalidRequestException("responsable",
                        "El responsable debe tener entre " + MIN_RESPONSIBLE + " y " + MAX_RESPONSIBLE
                                + " caracteres"))));
    }

    private static boolean isValidResponsible(String responsible) {
        return responsible.length() >= MIN_RESPONSIBLE && responsible.length() <= MAX_RESPONSIBLE;
    }

    private static void logAlert(String message, MedicalAlert alert) {
        log.atInfo()
                .addKeyValue("alertId", alert.getId())
                .addKeyValue("patientId", alert.getPatientId())
                .addKeyValue("status", alert.getStatus())
                .log(message);
    }
}
