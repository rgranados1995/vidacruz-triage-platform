package com.vidacruz.assistant.model.answer;

import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The excerpts handed to the language model, each with a short id ({@code E1}, {@code E2}...). It is also the judge
 * of RN-05: an answer is accepted only if it cites at least one of these ids, and the sources shown to the doctor are
 * taken from the excerpts, never from the text of the model, so a source cannot be invented.
 */
public final class ExcerptSet {

    private static final String ID_PREFIX = "E";

    private final Map<String, ProtocolExcerpt> byId = new LinkedHashMap<>();

    public ExcerptSet(List<ProtocolExcerpt> excerpts) {
        for (var excerpt : excerpts) {
            byId.put(ID_PREFIX + (byId.size() + 1), excerpt);
        }
    }

    /** An excerpt with the id the model has to cite. */
    public record NumberedExcerpt(String id, ProtocolExcerpt excerpt) {
    }

    public boolean hasExcerpts() {
        return !byId.isEmpty();
    }

    public List<NumberedExcerpt> numbered() {
        var numbered = new ArrayList<NumberedExcerpt>();
        byId.forEach((id, excerpt) -> numbered.add(new NumberedExcerpt(id, excerpt)));
        return numbered;
    }

    /** Checks the proposal of the model: only a cited, non-empty answer reaches the doctor (RN-05). */
    public GroundedAnswer ground(GeneratedAnswer generated) {
        if (generated.noProtocol()) {
            return GroundedAnswer.withoutContent(AnswerOutcome.DECLINED_BY_MODEL);
        }
        var text = generated.text() == null ? "" : generated.text().strip();
        var sources = citedSources(generated.excerptIds());
        return text.isEmpty() || sources.isEmpty()
                ? GroundedAnswer.withoutContent(AnswerOutcome.UNSUPPORTED_ANSWER)
                : new GroundedAnswer(new ClinicalAnswer(text, sources), AnswerOutcome.ANSWERED);
    }

    /** Sources of the cited excerpts, without repeats; empty when nothing is cited or any id was not handed out. */
    private List<Source> citedSources(List<String> excerptIds) {
        var sources = new LinkedHashSet<Source>();
        for (var id : excerptIds) {
            var excerpt = byId.get(id == null ? "" : id.strip());
            if (excerpt == null) {
                return List.of();
            }
            sources.add(new Source(excerpt.protocol(), excerpt.section()));
        }
        return List.copyOf(sources);
    }
}
