package com.vidacruz.assistant.model.answer;

import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The excerpts handed to the language model, each with a short id ({@code E1}, {@code E2}...). It is also the judge
 * of RN-05: an answer is accepted only if every sentence cites these ids and states no quantity they do not state,
 * and the sources shown to the doctor are taken from the excerpts, never from the text of the model, so a source
 * cannot be invented.
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

    /**
     * Checks the proposal of the model sentence by sentence (RN-05). Every sentence must be non-empty, cite only
     * excerpts it was given, and contain no number or unit its cited excerpts do not contain. One sentence that fails
     * discards the whole answer: the doctor gets only the notice, never a mix of supported and unsupported content.
     */
    public GroundedAnswer ground(GeneratedAnswer generated) {
        if (generated.noProtocol()) {
            return GroundedAnswer.withoutContent(AnswerOutcome.DECLINED_BY_MODEL);
        }
        var sentences = new ArrayList<String>();
        var sources = new LinkedHashSet<Source>();
        for (var claim : generated.claims()) {
            var text = claim == null || claim.text() == null ? "" : claim.text().strip();
            var cited = claim == null ? List.<ProtocolExcerpt>of() : cited(claim.excerptIds());
            if (text.isEmpty() || cited.isEmpty() || !statedBy(text, cited)) {
                return GroundedAnswer.withoutContent(AnswerOutcome.UNSUPPORTED_ANSWER);
            }
            sentences.add(text);
            cited.forEach(excerpt -> sources.add(new Source(excerpt.protocol(), excerpt.section())));
        }
        return sentences.isEmpty()
                ? GroundedAnswer.withoutContent(AnswerOutcome.UNSUPPORTED_ANSWER)
                : new GroundedAnswer(new ClinicalAnswer(String.join(" ", sentences), List.copyOf(sources)),
                        AnswerOutcome.ANSWERED);
    }

    /** The cited excerpts; empty when nothing is cited or any id was not handed out. */
    private List<ProtocolExcerpt> cited(List<String> excerptIds) {
        var cited = new ArrayList<ProtocolExcerpt>();
        for (var id : excerptIds) {
            var excerpt = byId.get(id == null ? "" : id.strip());
            if (excerpt == null) {
                return List.of();
            }
            cited.add(excerpt);
        }
        return cited;
    }

    /** Every quantity of the sentence appears in its cited excerpts (their code and section count, too). */
    private static boolean statedBy(String sentence, List<ProtocolExcerpt> cited) {
        var allowed = new StringBuilder();
        cited.forEach(excerpt -> allowed.append(excerpt.protocol()).append('\n').append(excerpt.section())
                .append('\n').append(excerpt.content()).append('\n'));
        return Quantities.inText(allowed.toString()).containsAll(Quantities.inText(sentence));
    }
}
