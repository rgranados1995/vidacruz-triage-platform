package com.vidacruz.triage.contract.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * CloudEvents 1.0 envelope in structured JSON mode.
 */
public record CloudEvent(
        @JsonProperty("specversion") String specVersion,
        @JsonProperty("id") UUID id,
        @JsonProperty("source") String source,
        @JsonProperty("type") String type,
        @JsonProperty("subject") String subject,
        @JsonProperty("time") Instant time,
        @JsonProperty("datacontenttype") String dataContentType,
        @JsonProperty("data") PatientEventData data) {
}
