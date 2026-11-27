package com.vidacruz.assistant.usecase.answerclinicalquestion;

import com.vidacruz.assistant.model.answer.AnswerOutcome;
import com.vidacruz.assistant.model.answer.ClinicalAnswer;
import com.vidacruz.assistant.model.answer.ExcerptSet;
import com.vidacruz.assistant.model.answer.Claim;
import com.vidacruz.assistant.model.answer.GeneratedAnswer;
import com.vidacruz.assistant.model.answer.Source;
import com.vidacruz.assistant.model.answer.gateways.AnswerGenerator;
import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;
import com.vidacruz.assistant.model.protocol.gateways.ProtocolRetriever;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The three gates of RN-05 with the language model mocked.
 */
@ExtendWith(MockitoExtension.class)
class AnswerClinicalQuestionUseCaseTest {

    private static final String HYPERTENSION = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?";
    private static final ProtocolExcerpt MEASURES = new ProtocolExcerpt("PROTO-001",
            "Crisis Hipertensiva, manejo inicial", "Medidas iniciales",
            "Reconfirmar la presión tras 5 minutos de reposo.", 0.82);

    @Mock
    private ProtocolRetriever retriever;
    @Mock
    private AnswerGenerator generator;

    @Test
    void shouldAnswerWithTheSourcesOfTheExcerptsTheModelCited() {
        when(retriever.retrieve(new ClinicalQuestion(HYPERTENSION))).thenReturn(Flux.just(MEASURES));
        when(generator.generate(any(), any())).thenReturn(Mono.just(
                new GeneratedAnswer(false,
                        List.of(new Claim("Reconfirmar la presión tras 5 minutos de reposo.", List.of("E1"))))));

        StepVerifier.create(useCase().answer(HYPERTENSION))
                .assertNext(grounded -> {
                    assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.ANSWERED);
                    assertThat(grounded.answer().sources())
                            .containsExactly(new Source("PROTO-001", "Medidas iniciales"));
                })
                .verifyComplete();

        var excerpts = ArgumentCaptor.forClass(ExcerptSet.class);
        verify(generator).generate(any(), excerpts.capture());
        assertThat(excerpts.getValue().numbered()).extracting(ExcerptSet.NumberedExcerpt::excerpt)
                .containsExactly(MEASURES);
    }

    @Test
    void shouldGiveTheNoticeWithoutCallingTheModelWhenNoProtocolMatches() {
        when(retriever.retrieve(any())).thenReturn(Flux.empty());

        StepVerifier.create(useCase().answer("¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?"))
                .assertNext(grounded -> {
                    assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.NO_MATCHING_PROTOCOL);
                    assertThat(grounded.answer()).isEqualTo(ClinicalAnswer.noProtocol());
                })
                .verifyComplete();
        verifyNoInteractions(generator);
    }

    @Test
    void shouldDiscardAnAnswerThatCitesNothingItWasGiven() {
        when(retriever.retrieve(any())).thenReturn(Flux.just(MEASURES));
        when(generator.generate(any(), any())).thenReturn(Mono.just(
                new GeneratedAnswer(false, List.of(new Claim("Iniciar metformina.", List.of("PROTO-999"))))));

        StepVerifier.create(useCase().answer(HYPERTENSION))
                .assertNext(grounded -> {
                    assertThat(grounded.outcome()).isEqualTo(AnswerOutcome.UNSUPPORTED_ANSWER);
                    assertThat(grounded.answer()).isEqualTo(ClinicalAnswer.noProtocol());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectAnInvalidQuestionBeforeSearching() {
        StepVerifier.create(useCase().answer("  "))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getError() == BusinessError.INVALID_QUESTION)
                .verify();
        verifyNoInteractions(retriever, generator);
    }

    @Test
    void shouldPassOnAnUnavailableModel() {
        when(retriever.retrieve(any())).thenReturn(Flux.just(MEASURES));
        when(generator.generate(any(), any())).thenReturn(Mono.error(
                new BusinessException(BusinessError.ASSISTANT_UNAVAILABLE, "")));

        StepVerifier.create(useCase().answer(HYPERTENSION))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && business.getError() == BusinessError.ASSISTANT_UNAVAILABLE)
                .verify();
    }

    private AnswerClinicalQuestionUseCase useCase() {
        return new AnswerClinicalQuestionUseCase(retriever, generator);
    }
}
