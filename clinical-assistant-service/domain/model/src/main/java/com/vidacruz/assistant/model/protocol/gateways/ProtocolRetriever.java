package com.vidacruz.assistant.model.protocol.gateways;

import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import reactor.core.publisher.Flux;

/**
 * Finds the sections of the official corpus related to a question.
 */
public interface ProtocolRetriever {

    /**
     * Sections relevant enough to the question, the closest first; empty when no protocol covers it.
     */
    Flux<ProtocolExcerpt> retrieve(ClinicalQuestion question);
}
