package com.vidacruz.assistant.model.answer.gateways;

import com.vidacruz.assistant.model.answer.ExcerptSet;
import com.vidacruz.assistant.model.answer.GeneratedAnswer;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import reactor.core.publisher.Mono;

/**
 * Language model that drafts an answer using only the excerpts it is given.
 */
public interface AnswerGenerator {

    /**
     * Emits the proposal of the model, or a {@link com.vidacruz.assistant.model.exception.BusinessException} with
     * {@link com.vidacruz.assistant.model.exception.BusinessError#ASSISTANT_UNAVAILABLE} when the model cannot be
     * reached.
     */
    Mono<GeneratedAnswer> generate(ClinicalQuestion question, ExcerptSet excerpts);
}
