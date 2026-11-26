package com.vidacruz.assistant.usecase.answerclinicalquestion;

import com.vidacruz.assistant.model.answer.ExcerptSet;
import com.vidacruz.assistant.model.answer.GroundedAnswer;
import com.vidacruz.assistant.model.answer.gateways.AnswerGenerator;
import com.vidacruz.assistant.model.protocol.gateways.ProtocolRetriever;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * RF-04 part A with RN-05. Three gates keep the answer inside the official corpus:
 * <ol>
 *     <li>no section of the corpus is close enough: the notice, without calling the model;</li>
 *     <li>the model drafts an answer using only the excerpts found;</li>
 *     <li>the answer is accepted only if it cites those excerpts, otherwise the notice again.</li>
 * </ol>
 */
@RequiredArgsConstructor
public class AnswerClinicalQuestionUseCase {

    private final ProtocolRetriever protocolRetriever;
    private final AnswerGenerator answerGenerator;

    /**
     * Emits the grounded answer, or a {@link com.vidacruz.assistant.model.exception.BusinessException} when the
     * question is invalid or the model cannot be reached.
     */
    public Mono<GroundedAnswer> answer(String questionText) {
        return Mono.fromCallable(() -> ClinicalQuestion.from(questionText))
                .flatMap(question -> protocolRetriever.retrieve(question)
                        .collectList()
                        .map(ExcerptSet::new)
                        .filter(ExcerptSet::hasExcerpts)
                        .flatMap(excerpts -> answerGenerator.generate(question, excerpts).map(excerpts::ground))
                        .switchIfEmpty(Mono.fromSupplier(GroundedAnswer::noMatchingProtocol)));
    }
}
