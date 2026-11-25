package com.vidacruz.triage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Environment of the integration tests: one PostgreSQL and one Kafka container for the whole run and the same
 * environment variables the deployment provides, so the tests read the real application.yaml and not the local
 * profile.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestEnvironment {

    /**
     * The logging system starts before the dynamic properties are registered, so the log level has to be given as an
     * inlined property of the test ({@code @SpringBootTest(properties = TestEnvironment.LOG_LEVEL)}).
     */
    static final String LOG_LEVEL = "LOG_LEVEL=INFO";

    static final String API_KEY = "integration-test-api-key";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.16-alpine");

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static final String EVENTS_TOPIC = "triaje.eventos";

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    static String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    static void register(DynamicPropertyRegistry registry) {
        registry.add("TRACING_SAMPLING_PROBABILITY", () -> "1.0");
        registry.add("API_KEY", () -> API_KEY);
        registry.add("API_DOCS_ENABLED", () -> "true");
        registry.add("KAFKA_BOOTSTRAP_SERVERS", KAFKA::getBootstrapServers);
        registry.add("TRIAGE_EVENTS_TOPIC", () -> EVENTS_TOPIC);
        registry.add("TRIAGE_EVENTS_PARTITIONS", () -> "3");
        registry.add("TRIAGE_EVENTS_REPLICAS", () -> "1");
        registry.add("SLA_MONITOR_INTERVAL", () -> "300ms");
        registry.add("SLA_MONITOR_BATCH_SIZE", () -> "50");
        registry.add("OUTBOX_POLL_INTERVAL", () -> "200ms");
        registry.add("OUTBOX_BATCH_SIZE", () -> "50");
        registry.add("OUTBOX_LEASE", () -> "10s");
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", () -> POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("DB_POOL_INITIAL_SIZE", () -> "1");
        registry.add("DB_POOL_MAX_SIZE", () -> "5");
        registry.add("SLA_MINUTES_CRITICAL", () -> "5");
        registry.add("SLA_MINUTES_URGENT", () -> "10");
        registry.add("SLA_MINUTES_URGENT_2", () -> "30");
        registry.add("SLA_MINUTES_NON_URGENT", () -> "60");
        registry.add("SLA_MINUTES_NON_URGENT_2", () -> "120");
        registry.add("ADMINISTRATIVE_COMPLAINTS",
                () -> "documentos,recolección de resultados,renovación de recetario,vacunación,seguimiento");
    }
}
