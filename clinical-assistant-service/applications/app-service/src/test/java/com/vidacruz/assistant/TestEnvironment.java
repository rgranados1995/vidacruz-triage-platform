package com.vidacruz.assistant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.File;

/**
 * Environment of the integration tests: the same variables the deployment provides, so the tests read the real
 * application.yaml. The embedding model is the real one (downloaded by the build); the Anthropic API is faked.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestEnvironment {

    /**
     * The logging system starts before the dynamic properties are registered, so the log level has to be given as an
     * inlined property of the test ({@code @SpringBootTest(properties = TestEnvironment.LOG_LEVEL)}).
     */
    static final String LOG_LEVEL = "LOG_LEVEL=INFO";

    static final String API_KEY = "integration-test-assistant-key";

    static void register(DynamicPropertyRegistry registry, String anthropicKey, String anthropicBaseUrl) {
        var model = new File(System.getProperty("embedding.model.dir"));
        registry.add("TRACING_SAMPLING_PROBABILITY", () -> "1.0");
        registry.add("ASSISTANT_API_KEY", () -> API_KEY);
        registry.add("API_DOCS_ENABLED", () -> "true");
        registry.add("ANTHROPIC_API_KEY", () -> anthropicKey);
        registry.add("adapters.claude.base-url", () -> anthropicBaseUrl);
        registry.add("LLM_MODEL", () -> "claude-opus-5-5");
        registry.add("LLM_EFFORT", () -> "medium");
        registry.add("LLM_TIMEOUT", () -> "5s");
        registry.add("RAG_TOP_K", () -> "3");
        registry.add("RAG_SIMILARITY_THRESHOLD", () -> "0.50");
        registry.add("EMBEDDING_MODEL_URI", () -> new File(model, "model.onnx").toURI().toString());
        registry.add("EMBEDDING_TOKENIZER_URI", () -> new File(model, "tokenizer.json").toURI().toString());
    }
}
