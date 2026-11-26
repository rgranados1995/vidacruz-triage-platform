package com.vidacruz.triage.api.alert;

import com.vidacruz.triage.api.alert.dto.AcknowledgeAlertRequest;
import com.vidacruz.triage.api.error.InvalidRequestException;
import com.vidacruz.triage.model.alert.AlertStatus;
import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.usecase.medicalalerts.AcknowledgeAlertUseCase;
import com.vidacruz.triage.usecase.medicalalerts.ListActiveAlertsUseCase;
import com.vidacruz.triage.usecase.medicalalerts.WatchAlertsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Handler without a Spring context: the stream (heartbeat, end, slow clients) and the reading of the acknowledgement.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AlertHandlerTest {

    private static final Duration HEARTBEAT = Duration.ofSeconds(15);
    private static final Instant ESCALATED_AT = Instant.parse("2026-11-25T10:05:00Z");

    @Mock
    private ListActiveAlertsUseCase listActiveAlerts;
    @Mock
    private WatchAlertsUseCase watchAlerts;
    @Mock
    private AcknowledgeAlertUseCase acknowledgeAlert;

    @Test
    void shouldSendAHeartbeatWhileThereAreNoChanges() {
        when(watchAlerts.watch()).thenReturn(Flux.never());

        StepVerifier.withVirtualTime(() -> handler().events())
                .expectSubscription()
                .expectNoEvent(HEARTBEAT.minusSeconds(1))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(event -> {
                    assertThat(event.comment()).isEqualTo("latido");
                    assertThat(event.data()).isNull();
                })
                .thenAwait(HEARTBEAT)
                .assertNext(event -> assertThat(event.comment()).isEqualTo("latido"))
                .thenCancel()
                .verify();
    }

    @Test
    void shouldEndTheStreamWhenTheAlertsEnd(CapturedOutput output) {
        when(watchAlerts.watch()).thenReturn(Flux.just(alert(1)));

        StepVerifier.create(handler().events())
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("alerta");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().id()).isEqualTo(1);
                })
                .verifyComplete();

        assertThat(output).contains("Alert stream opened", "Alert stream closed");
    }

    @Test
    void shouldCloseTheStreamOfAClientThatDoesNotKeepUp(CapturedOutput output) {
        when(watchAlerts.watch()).thenReturn(Flux.range(1, 5000).map(this::alert));

        // The client never asks for anything: its stream ends instead of piling up the changes
        StepVerifier.create(handler().events(), 0)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertThat(output).contains("Alert stream client too slow; closing its stream");
    }

    @Test
    void shouldKeepTheChangesOfAClientThatKeepsUpWithinTheBuffer() {
        when(watchAlerts.watch()).thenReturn(Flux.range(1, AlertHandler.STREAM_BUFFER).map(this::alert));

        StepVerifier.create(handler().events(), 0)
                .thenRequest(AlertHandler.STREAM_BUFFER)
                .expectNextCount(AlertHandler.STREAM_BUFFER)
                .verifyComplete();
    }

    @Test
    void shouldAcknowledgeWithTheResponsibleWithoutSurroundingSpaces(CapturedOutput output) {
        when(acknowledgeAlert.acknowledge(7, "Dra. Rojas")).thenReturn(Mono.just(alert(7)));

        StepVerifier.create(handler().acknowledge(request("7", " Dra. Rojas  ")))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        assertThat(output).contains("Alert acknowledged").contains("alertId=\"7\"");
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 100})
    void shouldAcceptAResponsibleAtTheLimits(int length) {
        var responsible = "a".repeat(length);
        when(acknowledgeAlert.acknowledge(7, responsible)).thenReturn(Mono.just(alert(7)));

        StepVerifier.create(handler().acknowledge(request("7", responsible)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 101})
    void shouldRejectAResponsibleOutsideTheLimits(int length) {
        StepVerifier.create(handler().acknowledge(request("7", "a".repeat(length))))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(InvalidRequestException.class,
                        invalid -> {
                            assertThat(invalid.getField()).isEqualTo("responsable");
                            assertThat(invalid.getReason())
                                    .isEqualTo("El responsable debe tener entre 3 y 100 caracteres");
                        }))
                .verify();
        verify(acknowledgeAlert, never()).acknowledge(anyLong(), anyString());
    }

    @Test
    void shouldRejectAMissingResponsibleOrBody() {
        var withoutResponsible = MockServerRequest.builder()
                .pathVariable(AlertHandler.ID_VARIABLE, "7")
                .body(Mono.just(new AcknowledgeAlertRequest(null)));
        var withoutBody = MockServerRequest.builder()
                .pathVariable(AlertHandler.ID_VARIABLE, "7")
                .body(Mono.empty());

        StepVerifier.create(handler().acknowledge(withoutResponsible)).verifyError(InvalidRequestException.class);
        StepVerifier.create(handler().acknowledge(withoutBody)).verifyError(InvalidRequestException.class);
    }

    @Test
    void shouldRejectAnIdThatIsNotANumber() {
        StepVerifier.create(handler().acknowledge(request("siete", "Dra. Rojas")))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(InvalidRequestException.class,
                        invalid -> assertThat(invalid.getField()).isEqualTo("id")))
                .verify();
    }

    @Test
    void shouldAnswerTheActiveAlerts() {
        when(listActiveAlerts.listActive()).thenReturn(Flux.just(alert(1)));

        StepVerifier.create(handler().listActive(MockServerRequest.builder().build()))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void shouldRequireAPositiveHeartbeat() {
        var zero = Duration.ZERO;
        var negative = Duration.ofSeconds(-1);

        assertThatIllegalArgumentException().isThrownBy(() -> new AlertStreamProperties(zero));
        assertThatIllegalArgumentException().isThrownBy(() -> new AlertStreamProperties(negative));
        assertThatIllegalArgumentException().isThrownBy(() -> new AlertStreamProperties(null));
    }

    private AlertHandler handler() {
        return new AlertHandler(listActiveAlerts, watchAlerts, acknowledgeAlert, new AlertStreamProperties(HEARTBEAT));
    }

    private static MockServerRequest request(String id, String responsible) {
        return MockServerRequest.builder()
                .pathVariable(AlertHandler.ID_VARIABLE, id)
                .body(Mono.just(new AcknowledgeAlertRequest(responsible)));
    }

    private MedicalAlert alert(long id) {
        return MedicalAlert.builder()
                .id(id)
                .patientId(UUID.randomUUID())
                .triageLevel(TriageLevel.URGENT)
                .slaMinutes(10)
                .registeredAt(ESCALATED_AT.minusSeconds(600))
                .escalatedAt(ESCALATED_AT)
                .status(AlertStatus.PENDING)
                .updatedAt(ESCALATED_AT)
                .build();
    }
}
