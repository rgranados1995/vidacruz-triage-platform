package com.vidacruz.assistant.model.answer;

import java.util.List;

/**
 * Answer given to the doctor: clinical content with at least one source of the corpus, or only the notice that no
 * protocol covers the question, with no clinical content at all (RN-05).
 *
 * @param text    answer, or the notice
 * @param sources sections that support the answer; empty for the notice
 */
public record ClinicalAnswer(String text, List<Source> sources) {

    /** The only text given when no protocol supports an answer. */
    public static final String NO_PROTOCOL_NOTICE =
            "No existe un protocolo disponible en el corpus oficial para esta pregunta.";

    public ClinicalAnswer {
        sources = List.copyOf(sources);
    }

    public static ClinicalAnswer noProtocol() {
        return new ClinicalAnswer(NO_PROTOCOL_NOTICE, List.of());
    }
}
