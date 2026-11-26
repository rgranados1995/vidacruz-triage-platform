package com.vidacruz.assistant.model.answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the language model proposed, before it is checked against the excerpts it was given. It is never shown as is.
 *
 * @param noProtocol the model found no answer in the excerpts
 * @param text       proposed answer
 * @param excerptIds ids of the excerpts the model says it used
 */
public record GeneratedAnswer(boolean noProtocol, String text, List<String> excerptIds) {

    public GeneratedAnswer {
        // Null ids are kept on purpose: an answer citing one is rejected by the excerpt set, not by a crash
        excerptIds = excerptIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(excerptIds));
    }

    /** The model declined to answer (it said no excerpt covers the question, or it refused). */
    public static GeneratedAnswer declined() {
        return new GeneratedAnswer(true, "", List.of());
    }
}
