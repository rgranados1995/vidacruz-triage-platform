package com.vidacruz.assistant.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.PatientStatus;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The published contract of the triage (CloudEvents 1.0 JSON) read by the assistant.
 */
class TriageEventParserTest {

    private static final UUID PATIENT = UUID.fromString("3f2b8c1e-5d4a-4f6b-9c2d-1a7e8b9c0d12");
    private static final String OCCURRED = "2026-11-26T10:04:12.345678Z";

    @ParameterizedTest
    @CsvSource({
        "paciente_registrado, CRITICO/REGISTRADO, CRITICAL/REGISTERED",
        "paciente_en_consulta, URGENTE/EN_CONSULTA, URGENT/IN_CONSULTATION",
        "paciente_alta, URGENTE_2/ALTA, URGENT_2/DISCHARGED",
        "paciente_escalado, NO_URGENTE/REGISTRADO, NON_URGENT/REGISTERED",
        "paciente_registrado, NO_URGENTE_2/REGISTRADO, NON_URGENT_2/REGISTERED"})
    void shouldReadTheStateOfThePatientFromEveryEventOfTheContract(String type, String contract, String expected)
            throws JsonProcessingException {
        var codes = contract.split("/");
        var values = expected.split("/");

        assertThat(TriageEventParser.parse(event(type, codes[0], codes[1], OCCURRED)))
                .contains(new PatientSnapshot(PATIENT, TriageLevel.valueOf(values[0]),
                        PatientStatus.valueOf(values[1]), Instant.parse(OCCURRED)));
    }

    @Test
    void shouldIgnoreAnEventTypeTheProjectionDoesNotKnow() throws JsonProcessingException {
        assertThat(TriageEventParser.parse(event("paciente_trasladado", "CRITICO", "REGISTRADO", OCCURRED)))
                .isEmpty();
        assertThat(TriageEventParser.parse("{\"specversion\": \"1.0\"}")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"type\": "})
    void shouldRejectAMessageThatIsNotJson(String message) {
        assertThatThrownBy(() -> TriageEventParser.parse(message)).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void shouldRejectAnEmptyOrMissingMessage() throws JsonProcessingException {
        assertThat(TriageEventParser.parse(null)).isEmpty();
        assertThat(TriageEventParser.parse("")).isEmpty();
    }

    @Test
    void shouldRejectAnEventWithoutAFieldOfTheContract() {
        var withoutLevel = event("paciente_registrado", "", "REGISTRADO", OCCURRED);
        var withoutData = "{\"type\": \"paciente_alta\"}";

        assertThatIllegalArgumentException().isThrownBy(() -> TriageEventParser.parse(withoutLevel))
                .withMessage("The event has no nivel");
        assertThatIllegalArgumentException().isThrownBy(() -> TriageEventParser.parse(withoutData))
                .withMessage("The event has no pacienteId");
    }

    @Test
    void shouldRejectValuesOutsideTheContract() {
        var level = event("paciente_registrado", "GRAVE", "REGISTRADO", OCCURRED);
        var status = event("paciente_registrado", "CRITICO", "OBSERVACION", OCCURRED);
        var date = event("paciente_registrado", "CRITICO", "REGISTRADO", "ayer");
        var id = "{\"type\": \"paciente_alta\", \"data\": {\"pacienteId\": \"12\", \"nivel\": \"CRITICO\", "
                + "\"estado\": \"ALTA\", \"ocurridoEn\": \"" + OCCURRED + "\"}}";

        assertThatIllegalArgumentException().isThrownBy(() -> TriageEventParser.parse(level))
                .withMessage("Unknown triage level GRAVE");
        assertThatIllegalArgumentException().isThrownBy(() -> TriageEventParser.parse(status))
                .withMessage("Unknown patient status OBSERVACION");
        assertThatThrownBy(() -> TriageEventParser.parse(date)).isInstanceOf(DateTimeException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> TriageEventParser.parse(id));
    }

    static String event(String type, String level, String status, String occurredAt) {
        return """
                {"specversion": "1.0", "id": "%s", "source": "/vidacruz/triage-service", "type": "%s",
                 "subject": "%s", "time": "%s", "datacontenttype": "application/json",
                 "data": {"pacienteId": "%s", "nivel": "%s", "estadoAnterior": null, "estado": "%s",
                          "escalado": false, "slaMinutos": 5, "fechaRegistro": "%s", "ocurridoEn": "%s"}}
                """.formatted(UUID.randomUUID(), type, PATIENT, occurredAt, PATIENT, level, status, occurredAt,
                occurredAt);
    }
}
