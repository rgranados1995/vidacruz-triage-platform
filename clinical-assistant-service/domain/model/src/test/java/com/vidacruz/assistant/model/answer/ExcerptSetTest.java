package com.vidacruz.assistant.model.answer;

import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RN-05: only an answer whose every sentence cites excerpts it was given, and states no quantity those excerpts do
 * not state, reaches the doctor, with the sources taken from those excerpts.
 */
class ExcerptSetTest {

    private static final ProtocolExcerpt MEASURES = excerpt("PROTO-001", "Medidas iniciales",
            "Reconfirmar la presión tras 5 minutos de reposo. No reducir la presión más de un 25% en la primera hora.");
    private static final ProtocolExcerpt CONTRAINDICATIONS = excerpt("PROTO-001", "Contraindicaciones",
            "Nifedipino sublingual.");
    private static final ProtocolExcerpt REHYDRATION = excerpt("PROTO-003", "Rehidratación",
            "Leve o moderada sin vómito → solución oral 50 ml/kg en 4 horas. Severa → vía intravenosa, bolo de "
                    + "20 ml/kg.");
    /** Built in each test (not in a static field): mutation testing cannot reach code run by a static initializer. */
    private final ExcerptSet excerpts = new ExcerptSet(List.of(MEASURES, CONTRAINDICATIONS, REHYDRATION));

    @Test
    void shouldNumberTheExcerptsInOrder() {
        assertThat(excerpts.hasExcerpts()).isTrue();
        assertThat(excerpts.numbered()).containsExactly(new ExcerptSet.NumberedExcerpt("E1", MEASURES),
                new ExcerptSet.NumberedExcerpt("E2", CONTRAINDICATIONS),
                new ExcerptSet.NumberedExcerpt("E3", REHYDRATION));
        assertThat(new ExcerptSet(List.of()).hasExcerpts()).isFalse();
    }

    @Test
    void shouldAcceptAnAnswerWhoseEverySentenceIsCitedWithTheSourcesOfItsExcerpts() {
        var grounded = excerpts.ground(answer(
                new Claim("  Reconfirmar la presión tras 5 minutos de reposo. ", List.of("E1", " E1")),
                new Claim("Está contraindicado el nifedipino sublingual (E2).", List.of("E2")),
                new Claim("No reducirla más de un 25 % en la primera hora.", List.of("E1"))));

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
        assertThat(grounded.answer().text()).isEqualTo("Reconfirmar la presión tras 5 minutos de reposo. Está "
                + "contraindicado el nifedipino sublingual (E2). No reducirla más de un 25 % en la primera hora.");
        assertThat(grounded.answer().sources()).containsExactly(new Source("PROTO-001", "Medidas iniciales"),
                new Source("PROTO-001", "Contraindicaciones"));
    }

    @Test
    void shouldAcceptTheQuantitiesOfTheCitedExcerptWrittenAnotherWay() {
        var grounded = excerpts.ground(answer(new Claim("Sin vómito: solución oral de 50 ml/kg en 4 h; en la "
                + "severa, bolo intravenoso de 20 ML/KG según PROTO-003.", List.of("E3"))));

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
        assertThat(grounded.answer().sources()).containsExactly(new Source("PROTO-003", "Rehidratación"));
    }

    @Test
    void shouldDiscardAValidCitationThatCarriesAnInventedDose() {
        var grounded = excerpts.ground(answer(
                new Claim("Reconfirmar la presión tras 5 minutos de reposo.", List.of("E1")),
                new Claim("Administrar labetalol 20 mg intravenoso.", List.of("E1"))));

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(grounded.answer()).isEqualTo(ClinicalAnswer.noProtocol());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Bolo de 30 ml/kg.",
        "Bolo de 20 mg/kg.",
        "Solución oral 50 ml/kg en 6 horas.",
        "Bolo de 20,5 ml/kg."
    })
    void shouldDiscardASentenceWhoseQuantityItsExcerptDoesNotState(String sentence) {
        assertThat(excerpts.ground(answer(new Claim(sentence, List.of("E3")))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
    }

    @Test
    void shouldCheckEachSentenceOnlyAgainstTheExcerptsItCites() {
        // 20 ml/kg is in E3, but this sentence only cites E1
        var grounded = excerpts.ground(answer(new Claim("Bolo de 20 ml/kg.", List.of("E1")),
                new Claim("Bolo intravenoso en la severa.", List.of("E3"))));

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
    }

    @Test
    void shouldGiveOnlyTheNoticeWhenTheModelFindsNoAnswer() {
        var grounded = excerpts.ground(GeneratedAnswer.declined());

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.DECLINED_BY_MODEL);
        assertThat(grounded.answer()).isEqualTo(ClinicalAnswer.noProtocol());
    }

    @Test
    void shouldDiscardAnAnswerThatCitesAnExcerptItWasNotGiven() {
        var grounded = excerpts.ground(answer(new Claim("Metformina como primera línea.", List.of("E1", "E7"))));

        assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(grounded.answer()).isEqualTo(ClinicalAnswer.noProtocol());
    }

    @Test
    void shouldDiscardAnAnswerWithASentenceWithoutCitations() {
        var cited = new Claim("Reconfirmar la presión.", List.of("E1"));

        assertThat(excerpts.ground(answer(cited, new Claim("Texto sin fuente.", List.of()))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(excerpts.ground(answer(cited, new Claim("Texto sin fuente.", null))).answer())
                .isEqualTo(ClinicalAnswer.noProtocol());
        assertThat(excerpts.ground(answer(new Claim("Texto.", Arrays.asList("E1", null)))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
    }

    @Test
    void shouldDiscardAnEmptyOrMissingSentenceEvenIfTheOthersCite() {
        var cited = new Claim("Reconfirmar la presión.", List.of("E1"));

        assertThat(excerpts.ground(answer(cited, new Claim("   ", List.of("E1")))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(excerpts.ground(answer(cited, new Claim(null, List.of("E1")))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(excerpts.ground(new GeneratedAnswer(false, Arrays.asList(cited, null))).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
    }

    @Test
    void shouldDiscardAnAnswerWithoutSentences() {
        assertThat(excerpts.ground(new GeneratedAnswer(false, List.of())).outcome())
                .isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
        assertThat(excerpts.ground(new GeneratedAnswer(false, null)).answer()).isEqualTo(ClinicalAnswer.noProtocol());
    }

    @Test
    void shouldKeepTheNoticeFreeOfClinicalContent() {
        var notice = ClinicalAnswer.noProtocol();

        assertThat(notice.text()).isEqualTo(ClinicalAnswer.NO_PROTOCOL_NOTICE);
        assertThat(notice.sources()).isEmpty();
        assertThat(GroundedAnswer.noMatchingProtocol().outcome()).isEqualTo(AnswerOutcome.NO_MATCHING_PROTOCOL);
    }

    private static GeneratedAnswer answer(Claim... claims) {
        return new GeneratedAnswer(false, List.of(claims));
    }

    private static ProtocolExcerpt excerpt(String protocol, String section, String content) {
        return new ProtocolExcerpt(protocol, "Protocolo " + protocol, section, content, 0.8);
    }
}
