package com.vidacruz.assistant.corpus;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the search over the protocol corpus.
 *
 * @param topK                most sections handed to the language model
 * @param similarityThreshold least similarity (0 to 1) for a section to count as relevant; below it the question
 *                            has no protocol
 * @param embeddingModel      URI of the ONNX embedding model
 * @param embeddingTokenizer  URI of its tokenizer
 */
@ConfigurationProperties(prefix = "adapters.protocol-corpus")
public record ProtocolCorpusProperties(int topK, double similarityThreshold, String embeddingModel,
                                       String embeddingTokenizer) {

    public ProtocolCorpusProperties {
        if (topK <= 0) {
            throw new IllegalArgumentException("top-k must be positive, but it is " + topK);
        }
        if (similarityThreshold <= 0 || similarityThreshold >= 1) {
            throw new IllegalArgumentException("similarity-threshold must be between 0 and 1 (exclusive), but it is "
                    + similarityThreshold);
        }
        if (embeddingModel == null || embeddingModel.isBlank() || embeddingTokenizer == null
                || embeddingTokenizer.isBlank()) {
            throw new IllegalArgumentException("embedding-model and embedding-tokenizer are required");
        }
    }
}
