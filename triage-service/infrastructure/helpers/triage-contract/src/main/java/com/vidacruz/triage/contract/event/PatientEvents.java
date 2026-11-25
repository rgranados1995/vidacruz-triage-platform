package com.vidacruz.triage.contract.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidacruz.triage.contract.ContractCodes;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Builds the message of the topic {@code triaje.eventos} for a change of a patient: a CloudEvent whose type is the
 * event name of the statement and whose subject is the patient id (also the Kafka key, so the events of a patient
 * keep their order).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PatientEvents {

    public static final String CONTENT_TYPE = "application/cloudevents+json; charset=UTF-8";
    static final String SPEC_VERSION = "1.0";
    static final String SOURCE = "/vidacruz/triage-service";
    static final String DATA_CONTENT_TYPE = "application/json";

    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /**
     * JSON of the event of the change.
     *
     * @throws JsonProcessingException if the event cannot be serialized; callers run it inside a reactive
     *                                 {@code fromCallable}, so it becomes an error signal
     */
    public static String toJson(PatientChange change, UUID eventId) throws JsonProcessingException {
        return JSON.writeValueAsString(toCloudEvent(change, eventId));
    }

    public static CloudEvent toCloudEvent(PatientChange change, UUID eventId) {
        var patient = change.patient();
        var entry = change.auditEntry();
        var data = new PatientEventData(patient.getId(), ContractCodes.levelCode(patient.getTriageLevel()),
                statusCodeOrNull(entry.previousStatus()), ContractCodes.statusCode(entry.newStatus()),
                patient.isEscalated(), patient.getSlaMinutes(), patient.getRegisteredAt(), entry.occurredAt());
        return new CloudEvent(SPEC_VERSION, eventId, SOURCE, ContractCodes.eventType(entry.eventType()),
                patient.getId().toString(), entry.occurredAt(), DATA_CONTENT_TYPE, data);
    }

    private static String statusCodeOrNull(PatientStatus status) {
        return status == null ? null : ContractCodes.statusCode(status);
    }
}
