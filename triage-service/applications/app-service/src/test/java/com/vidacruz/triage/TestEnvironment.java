package com.vidacruz.triage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Environment of the integration tests: one PostgreSQL container for the whole run and the same environment
 * variables the deployment provides, so the tests read the real application.yaml and not the local profile.
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

    static {
        POSTGRES.start();
    }

    static void register(DynamicPropertyRegistry registry) {
        registry.add("TRACING_SAMPLING_PROBABILITY", () -> "1.0");
        registry.add("API_KEY", () -> API_KEY);
        registry.add("API_DOCS_ENABLED", () -> "true");
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
