package com.vidacruz.assistant.corpus;

import com.vidacruz.assistant.model.protocol.ProtocolExcerpt;
import com.vidacruz.assistant.model.protocol.gateways.ProtocolRetriever;
import com.vidacruz.assistant.model.question.ClinicalQuestion;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;

/**
 * Semantic search over the corpus. The embedding of the question is computed on the CPU, so the search runs on the
 * bounded elastic scheduler and never on the event loop.
 */
public class VectorStoreProtocolRetriever implements ProtocolRetriever {

    static final String PROTOCOL = "protocol";
    static final String TITLE = "title";
    static final String SECTION = "section";
    static final String CONTENT = "content";

    private final VectorStore vectorStore;
    private final ProtocolCorpusProperties properties;

    public VectorStoreProtocolRetriever(VectorStore vectorStore, ProtocolCorpusProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Override
    public Flux<ProtocolExcerpt> retrieve(ClinicalQuestion question) {
        return Mono.fromCallable(() -> vectorStore.similaritySearch(SearchRequest.builder()
                        .query(question.text())
                        .topK(properties.topK())
                        .similarityThreshold(properties.similarityThreshold())
                        .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(VectorStoreProtocolRetriever::toExcerpt);
    }

    static Document toDocument(CorpusSection section) {
        return Document.builder()
                .id(section.protocol() + "/" + section.section())
                .text(section.searchableText())
                .metadata(PROTOCOL, section.protocol())
                .metadata(TITLE, section.title())
                .metadata(SECTION, section.section())
                .metadata(CONTENT, section.content())
                .build();
    }

    private static ProtocolExcerpt toExcerpt(Document document) {
        var metadata = document.getMetadata();
        return new ProtocolExcerpt(String.valueOf(metadata.get(PROTOCOL)), String.valueOf(metadata.get(TITLE)),
                String.valueOf(metadata.get(SECTION)), String.valueOf(metadata.get(CONTENT)),
                Objects.requireNonNullElse(document.getScore(), 0.0));
    }
}
