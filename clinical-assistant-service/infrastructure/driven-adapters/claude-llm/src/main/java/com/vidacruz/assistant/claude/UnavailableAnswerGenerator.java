package com.vidacruz.assistant.claude;

import com.vidacruz.assistant.model.answer.ExcerptSet;
import com.vidacruz.assistant.model.answer.GeneratedAnswer;
import com.vidacruz.assistant.model.answer.gateways.AnswerGenerator;
import com.vidacruz.assistant.model.exception.BusinessError;
import com.vidacruz.assistant.model.exception.BusinessException;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import reactor.core.publisher.Mono;

/**
 * Used when no key of the AI provider is configured: the service still starts and searches the corpus, and every
 * question that needs the model is answered as unavailable (degraded mode).
 */
public class UnavailableAnswerGenerator implements AnswerGenerator {

    @Override
    public Mono<GeneratedAnswer> generate(ClinicalQuestion question, ExcerptSet excerpts) {
        return Mono.error(() -> new BusinessException(BusinessError.ASSISTANT_UNAVAILABLE, ""));
    }
}
