package com.vidacruz.assistant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.io.File;
import java.util.UUID;

/**
 * Environment of the integration tests: the same variables the deployment provides, so the tests read the real
 * application.yaml. The embedding model, Kafka and Redis are real (one container of each for the whole run); the
 * Anthropic API is faked.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestEnvironment {

    /**
     * The logging system starts before the dynamic properties are registered, so the log level has to be given as an
     * inlined property of the test ({@code @SpringBootTest(properties = TestEnvironment.LOG_LEVEL)}).
     */
    static final String LOG_LEVEL = "LOG_LEVEL=INFO";

    static final String API_KEY = "integration-test-assistant-key";
    static final String EVENTS_TOPIC = "triaje.eventos";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.12-alpine")
            .withExposedPorts(6379);

    static {
        KAFKA.start();
        REDIS.start();
    }

    static String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    static void register(DynamicPropertyRegistry registry, String anthropicKey, String anthropicBaseUrl) {
        var model = new File(System.getProperty("embedding.model.dir"));
        registry.add("TRACING_SAMPLING_PROBABILITY", () -> "1.0");
        registry.add("ASSISTANT_API_KEY", () -> API_KEY);
        registry.add("API_DOCS_ENABLED", () -> "true");
        registry.add("ANTHROPIC_API_KEY", () -> anthropicKey);
        registry.add("adapters.claude.base-url", () -> anthropicBaseUrl);
        registry.add("LLM_MODEL", () -> "claude-opus-5-5");
        registry.add("LLM_EFFORT", () -> "medium");
        registry.add("LLM_TIMEOUT", () -> "2s");
        registry.add("LLM_RETRY_MAX_ATTEMPTS", () -> "2");
        registry.add("LLM_RETRY_BACKOFF", () -> "20ms");
        registry.add("RAG_TOP_K", () -> "3");
        registry.add("RAG_SIMILARITY_THRESHOLD", () -> "0.50");
        registry.add("EMBEDDING_MODEL_URI", () -> new File(model, "model.onnx").toURI().toString());
        registry.add("EMBEDDING_TOKENIZER_URI", () -> new File(model, "tokenizer.json").toURI().toString());
        registry.add("KAFKA_BOOTSTRAP_SERVERS", KAFKA::getBootstrapServers);
        registry.add("TRIAGE_EVENTS_TOPIC", () -> EVENTS_TOPIC);
        // A group per application context: each context of the run reads every partition on its own
        var group = "assistant-test-" + UUID.randomUUID();
        registry.add("URGENCY_CONSUMER_GROUP", () -> group);
        registry.add("REDIS_HOST", REDIS::getHost);
        registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
    }
}
