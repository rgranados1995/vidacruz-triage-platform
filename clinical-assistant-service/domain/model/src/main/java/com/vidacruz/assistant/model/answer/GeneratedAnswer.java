package com.vidacruz.assistant.model.answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the language model proposed, before it is checked against the excerpts it was given. It is never shown as is.
 *
 * @param noProtocol the model found no answer in the excerpts
 * @param claims     the sentences of the proposed answer, each with the excerpts the model says state it
 */
public record GeneratedAnswer(boolean noProtocol, List<Claim> claims) {

    public GeneratedAnswer {
        // Null sentences are kept on purpose: an answer with one is rejected by the excerpt set, not by a crash
        claims = claims == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(claims));
    }

    /** The model declined to answer (it said no excerpt covers the question, or it refused). */
    public static GeneratedAnswer declined() {
        return new GeneratedAnswer(true, List.of());
    }
}
