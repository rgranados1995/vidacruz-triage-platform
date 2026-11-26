package com.vidacruz.assistant.corpus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds, once at startup, the in-memory vector store of the official corpus ({@code classpath:corpus/*.md}), with
 * a multilingual embedding model that runs inside the JVM: no external service and no key are needed to search.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ProtocolCorpusProperties.class)
public class ProtocolCorpusConfig {

    private static final String CORPUS = "classpath:corpus/*.md";

    @Bean
    public TransformersEmbeddingModel protocolEmbeddingModel(ProtocolCorpusProperties properties) throws Exception {
        // NONE: the metadata (protocol code, raw content) is not mixed into the embedded text
        var model = new TransformersEmbeddingModel(MetadataMode.NONE);
        model.setModelResource(properties.embeddingModel());
        model.setTokenizerResource(properties.embeddingTokenizer());
        // file: URIs are read in place (Spring AI copies only remote resources to its cache)
        model.afterPropertiesSet();
        return model;
    }

    @Bean
    public SimpleVectorStore protocolVectorStore(EmbeddingModel protocolEmbeddingModel) throws IOException {
        var sections = loadCorpus();
        var store = SimpleVectorStore.builder(protocolEmbeddingModel).build();
        store.add(sections.stream().map(VectorStoreProtocolRetriever::toDocument).toList());
        log.atInfo().addKeyValue("sections", sections.size()).log("Protocol corpus loaded");
        return store;
    }

    @Bean
    public VectorStoreProtocolRetriever protocolRetriever(SimpleVectorStore protocolVectorStore,
                                                          ProtocolCorpusProperties properties) {
        return new VectorStoreProtocolRetriever(protocolVectorStore, properties);
    }

    static List<CorpusSection> loadCorpus() throws IOException {
        var sections = new ArrayList<CorpusSection>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(CORPUS)) {
            sections.addAll(CorpusParser.parse(resource.getFilename(),
                    resource.getContentAsString(StandardCharsets.UTF_8)));
        }
        if (sections.isEmpty()) {
            throw new IllegalStateException("The protocol corpus is empty: no file matches " + CORPUS);
        }
        return sections;
    }
}
