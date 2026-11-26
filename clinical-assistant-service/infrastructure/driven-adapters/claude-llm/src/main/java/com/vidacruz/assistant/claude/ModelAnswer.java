package com.vidacruz.assistant.claude;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Shape the answer of Claude must have (structured outputs): the SDK derives the JSON schema from this record and the
 * API enforces it.
 *
 * @param noProtocol true when the excerpts do not answer the question
 * @param answer     the answer, in Spanish, based only on the excerpts; empty when noProtocol is true
 * @param excerptIds ids of every excerpt used, such as E1
 */
public record ModelAnswer(
        @JsonPropertyDescription("true when the excerpts do not answer the question") boolean noProtocol,
        @JsonPropertyDescription("the answer in Spanish, based only on the excerpts; empty when noProtocol is true")
        String answer,
        @JsonPropertyDescription("ids of every excerpt used in the answer, such as E1") List<String> excerptIds) {
}
