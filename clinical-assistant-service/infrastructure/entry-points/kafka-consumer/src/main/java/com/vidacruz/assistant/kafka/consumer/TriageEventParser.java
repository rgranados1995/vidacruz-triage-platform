package com.vidacruz.assistant.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.assistant.model.urgency.PatientSnapshot;
import com.vidacruz.assistant.model.urgency.PatientStatus;
import com.vidacruz.assistant.model.urgency.TriageLevel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads an event of {@code triaje.eventos} (CloudEvents 1.0 JSON) as published by the triage. The assistant does not
 * share code with the triage: it depends only on this published contract.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TriageEventParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Every event of the contract carries the whole state of the patient, so all of them feed the projection. */
    private static final Set<String> PATIENT_EVENTS =
            Set.of("paciente_registrado", "paciente_en_consulta", "paciente_alta", "paciente_escalado");

    /**
     * @return the state of the patient, or empty for a type of event the projection does not know
     * @throws JsonProcessingException  when the message is not JSON
     * @throws IllegalArgumentException when a field of the contract is missing or has an unknown value
     */
    static Optional<PatientSnapshot> parse(String message) throws JsonProcessingException {
        var event = JSON.readTree(message == null ? "" : message);
        if (event == null || !PATIENT_EVENTS.contains(event.path("type").asText())) {
            return Optional.empty();
        }
        var data = event.path("data");
        return Optional.of(new PatientSnapshot(
                UUID.fromString(required(data, "pacienteId")),
                level(required(data, "nivel")),
                status(required(data, "estado")),
                Instant.parse(required(data, "ocurridoEn"))));
    }

    private static String required(JsonNode data, String field) {
        var value = data.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("The event has no " + field);
        }
        return value.asText();
    }

    private static TriageLevel level(String code) {
        return switch (code) {
            case "CRITICO" -> TriageLevel.CRITICAL;
            case "URGENTE" -> TriageLevel.URGENT;
            case "URGENTE_2" -> TriageLevel.URGENT_2;
            case "NO_URGENTE" -> TriageLevel.NON_URGENT;
            case "NO_URGENTE_2" -> TriageLevel.NON_URGENT_2;
            default -> throw new IllegalArgumentException("Unknown triage level " + code);
        };
    }

    private static PatientStatus status(String code) {
        return switch (code) {
            case "REGISTRADO" -> PatientStatus.REGISTERED;
            case "EN_CONSULTA" -> PatientStatus.IN_CONSULTATION;
            case "ALTA" -> PatientStatus.DISCHARGED;
            default -> throw new IllegalArgumentException("Unknown patient status " + code);
        };
    }
}
