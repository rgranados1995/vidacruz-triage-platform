package com.vidacruz.assistant.model.answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One sentence of the answer proposed by the model, with the excerpts the model says state it. Each sentence is
 * checked on its own against its excerpts (RN-05), so a valid citation cannot carry an invented fact along.
 *
 * @param text       the sentence
 * @param excerptIds ids of the excerpts that state it, such as {@code E1}
 */
public record Claim(String text, List<String> excerptIds) {

    public Claim {
        // Null ids are kept on purpose: a sentence citing one is rejected by the excerpt set, not by a crash
        excerptIds = excerptIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(excerptIds));
    }
}
