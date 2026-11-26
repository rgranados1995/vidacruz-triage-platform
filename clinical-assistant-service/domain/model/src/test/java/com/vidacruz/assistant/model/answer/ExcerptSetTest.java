package com.vidacruz.assistant.model.answer;

import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RN-05: only a non-empty answer that cites excerpts it was given reaches the doctor, with the sources taken from
 * those excerpts.
 */
class ExcerptSetTest {

    private static final ProtocolExcerpt MEASURES = excerpt("PROTO-001", "Medidas iniciales");
    private static final ProtocolExcerpt CONTRAINDICATIONS = excerpt("PROTO-001", "Contraindicaciones");
    /** Built in each test (not in a static field): mutation testing cannot reach code run by a static initializer. */
    private final ExcerptSet excerpts = new ExcerptSet(List.of(MEASURES, CONTRAINDICATIONS));

    @Test
    void shouldNumberTheExcerptsInOrder() {
        assertThat(excerpts.hasExcerpts()).isTrue();
        assertThat(excerpts.numbered()).containsExactly(new ExcerptSet.NumberedExcerpt("E1", MEASURES),
                new ExcerptSet.NumberedExcerpt("E2", CONTRAINDICATIONS));
        assertThat(new ExcerptSet(List.of()).hasExcerpts()).isFalse();
    }

    @Test
    void shouldAcceptACitedAnswerWithTheSourcesOfItsExcerpts() {
        var grounded = excerpts.ground(new GeneratedAnswer(false, "  Reconfirmar la presión tras 5 minutos. ",
                List.of("E1", " E2", "E1")));

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
        assertThat(grounded.answer().text()).isEqualTo("Reconfirmar la presión tras 5 minutos.");
        assertThat(grounded.answer().sources()).containsExactly(new Source("PROTO-001", "Medidas iniciales"),
                new Source("PROTO-001", "Contraindicaciones"));
    }

    @Test
    void shouldGiveOnlyTheNoticeWhenTheModelFindsNoAnswer() {
        var grounded = excerpts.ground(GeneratedAnswer.declined());

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.DECLINED_BY_MODEL);
        assertThat(grounded.answer()).isEqualTo(ClinicalAnswer.noProtocol());
    }

    @Test
    void shouldDiscardAnAnswerThatCitesAnExcerptItWasNotGiven() {
        var grounded = excerpts.ground(new GeneratedAnswer(false, "Metformina como primera línea.",
                List.of("E1", "E7")));

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(grounded.answer()).isEqualTo(ClinicalAnswer.noProtocol());
    }

    @Test
    void shouldDiscardAnAnswerWithoutCitations() {
        assertThat(excerpts.ground(new GeneratedAnswer(false, "Texto sin fuente.", List.of())).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(excerpts.ground(new GeneratedAnswer(false, "Texto sin fuente.", null)).answer())
                .isEqualTo(ClinicalAnswer.noProtocol());
        assertThat(excerpts.ground(new GeneratedAnswer(false, "Texto.", Arrays.asList("E1", null))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
    }

    @Test
    void shouldDiscardAnEmptyAnswerEvenIfItCites() {
        assertThat(excerpts.ground(new GeneratedAnswer(false, "   ", List.of("E1"))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(excerpts.ground(new GeneratedAnswer(false, null, List.of("E1"))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
    }

    @Test
    void shouldKeepTheNoticeFreeOfClinicalContent() {
        var notice = ClinicalAnswer.noProtocol();

        assertThat(notice.text()).isEqualTo(ClinicalAnswer.NO_PROTOCOL_NOTICE);
        assertThat(notice.sources()).isEmpty();
        assertThat(GroundedAnswer.noMatchingProtocol().outcome()).isEqualTo(AnswerOutcome.NO_MATCHING_PROTOCOL);
    }

    private static ProtocolExcerpt excerpt(String protocol, String section) {
        return new ProtocolExcerpt(protocol, "Crisis Hipertensiva, manejo inicial", section, "Texto de " + section,
                0.8);
    }
}
