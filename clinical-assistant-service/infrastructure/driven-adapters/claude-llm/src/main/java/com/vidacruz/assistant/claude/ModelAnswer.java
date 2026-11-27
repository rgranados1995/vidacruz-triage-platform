package com.vidacruz.assistant.claude;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Shape the answer of Claude must have (structured outputs): the SDK derives the JSON schema from this record and the
 * API enforces it. The answer comes sentence by sentence, each with its own citations, so each sentence can be checked
 * against the excerpts it cites (RN-05).
 *
 * @param noProtocol true when the excerpts do not answer the question
 * @param claims     the sentences of the answer; empty when noProtocol is true
 */
public record ModelAnswer(
        @JsonPropertyDescription("true when the excerpts do not answer the question") boolean noProtocol,
        @JsonPropertyDescription("the answer, one sentence per item; empty when noProtocol is true")
        List<ModelClaim> claims) {

    /**
     * One sentence of the answer.
     *
     * @param text       the sentence in Spanish, stated by the cited excerpts
     * @param excerptIds ids of every excerpt that states it, such as E1
     */
    public record ModelClaim(
            @JsonPropertyDescription("one sentence in Spanish, stated by the cited excerpts") String text,
            @JsonPropertyDescription("ids of every excerpt that states this sentence, such as E1")
            List<String> excerptIds) {
    }
}
