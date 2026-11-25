package com.vidacruz.triage.r2dbc.patient;

import com.vidacruz.triage.model.audit.AuditEntry;
import com.vidacruz.triage.model.audit.PatientEventType;
import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.triage.TriageLevel;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the adapter against a real PostgreSQL with the migrations of the module applied.
 */
@Testcontainers
class PostgresPatientRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.16-alpine");

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-25T10:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(300);
    private static final String COUNT_AUDIT = "SELECT COUNT(*) AS total FROM clinical_audit WHERE patient_id = :id";

    private static ConnectionFactory connectionFactory;

    private DatabaseClient client;
    private PostgresPatientRepository repository;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build());
    }

    @BeforeEach
    void setUp() {
        client = DatabaseClient.create(connectionFactory);
        var transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        repository = new PostgresPatientRepository(client, transactions);
    }

    @Test
    void shouldStoreANewPatientWithItsRegistrationAudit() {
        var patient = newPatient();

        StepVerifier.create(repository.register(PatientChange.registration(patient)))
                .expectNext(patient)
                .verifyComplete();

        StepVerifier.create(repository.findById(patient.getId()))
                .assertNext(found -> assertThat(found).usingRecursiveComparison().isEqualTo(patient))
                .verifyComplete();
        StepVerifier.create(client.sql("""
                        SELECT event_type, previous_status, new_status, occurred_at
                          FROM clinical_audit WHERE patient_id = :id""")
                        .bind("id", patient.getId()).fetch().one())
                .assertNext(row -> {
                    assertThat(row).containsEntry("event_type", "REGISTERED").containsEntry("new_status", "REGISTERED");
                    assertThat(row.get("previous_status")).isNull();
                    assertThat(row.get("occurred_at")).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldFindNothingForAnUnknownPatient() {
        StepVerifier.create(repository.findById(UUID.randomUUID())).verifyComplete();
    }

    @Test
    void shouldApplyATransitionAndAuditIt() {
        var patient = register(newPatient());

        StepVerifier.create(repository.update(PatientChange.transition(patient, PatientStatus.IN_CONSULTATION, LATER)))
                .expectNextMatches(updated -> updated.getStatus() == PatientStatus.IN_CONSULTATION)
                .verifyComplete();

        StepVerifier.create(repository.findById(patient.getId()))
                .assertNext(found -> {
                    assertThat(found.getStatus()).isEqualTo(PatientStatus.IN_CONSULTATION);
                    assertThat(found.getStatusChangedAt()).isEqualTo(LATER);
                    assertThat(found.getConsultationStartedAt()).isEqualTo(LATER);
                })
                .verifyComplete();
        assertAuditCount(patient.getId(), 2);
    }

    @Test
    void shouldRejectAChangeComputedFromAnOutdatedStatusWithoutAuditingIt() {
        var patient = register(newPatient());
        var discharge = PatientChange.transition(patient, PatientStatus.DISCHARGED, LATER);
        var staleConsultation = PatientChange.transition(patient, PatientStatus.IN_CONSULTATION, LATER);
        StepVerifier.create(repository.update(discharge)).expectNextCount(1).verifyComplete();

        StepVerifier.create(repository.update(staleConsultation))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(BusinessException.class, business -> {
                            assertThat(business.getError()).isEqualTo(BusinessError.INVALID_STATUS_TRANSITION);
                            assertThat(business.getDetail()).isEqualTo(PostgresPatientRepository.CONCURRENT_CHANGE);
                        }))
                .verify();

        StepVerifier.create(repository.findById(patient.getId()))
                .expectNextMatches(found -> found.getStatus() == PatientStatus.DISCHARGED)
                .verifyComplete();
        assertAuditCount(patient.getId(), 2);
    }

    @Test
    void shouldNotStoreThePatientWhenItsAuditCannotBeStored() {
        var patient = newPatient();
        var auditOfAnotherPatient = new AuditEntry(UUID.randomUUID(), PatientEventType.REGISTERED, null,
                PatientStatus.REGISTERED, REGISTERED_AT);

        StepVerifier.create(repository.register(new PatientChange(patient, null, auditOfAnotherPatient)))
                .expectError()
                .verify();

        StepVerifier.create(repository.findById(patient.getId())).verifyComplete();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "UPDATE clinical_audit SET new_status = 'DISCHARGED'",
            "DELETE FROM clinical_audit",
            "TRUNCATE clinical_audit CASCADE"})
    void shouldKeepTheClinicalAuditAppendOnly(String statement) {
        var patient = register(newPatient());

        StepVerifier.create(client.sql(statement).fetch().rowsUpdated())
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("clinical_audit is append-only"))
                .verify();

        assertAuditCount(patient.getId(), 1);
    }

    private Patient register(Patient patient) {
        StepVerifier.create(repository.register(PatientChange.registration(patient))).expectNextCount(1)
                .verifyComplete();
        return patient;
    }

    private void assertAuditCount(UUID patientId, long expected) {
        StepVerifier.create(client.sql(COUNT_AUDIT).bind("id", patientId).map(row -> row.get("total", Long.class))
                        .one())
                .expectNext(expected)
                .verifyComplete();
    }

    private static Patient newPatient() {
        return Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Ana María Pérez")
                .age(54)
                .vitalSigns(VitalSigns.builder()
                        .heartRate(130).systolicPressure(180).diastolicPressure(110).oxygenSaturation(97)
                        .temperature(new BigDecimal("36.8")).painLevel(8).glasgowComaScale(15)
                        .build())
                .chiefComplaint("Dolor torácico")
                .triageLevel(TriageLevel.CRITICAL)
                .slaMinutes(5)
                .status(PatientStatus.REGISTERED)
                .escalated(false)
                .registeredAt(REGISTERED_AT)
                .statusChangedAt(REGISTERED_AT)
                .build();
    }
}
