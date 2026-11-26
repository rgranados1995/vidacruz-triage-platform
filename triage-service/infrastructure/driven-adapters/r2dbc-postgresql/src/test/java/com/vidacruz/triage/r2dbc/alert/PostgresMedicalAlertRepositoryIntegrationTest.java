package com.vidacruz.triage.r2dbc.alert;

import com.vidacruz.triage.model.alert.AlertStatus;
import com.vidacruz.triage.model.alert.MedicalAlert;
import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.r2dbc.outbox.OutboxWriter;
import com.vidacruz.triage.r2dbc.patient.PostgresPatientRepository;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-06 against a real PostgreSQL: the alerts follow the patient changes inside their transactions, and the
 * listener hears every committed change, also after losing its connection.
 */
@Testcontainers
class PostgresMedicalAlertRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.16-alpine");

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String COUNT_ALERTS = "SELECT COUNT(*) AS total FROM medical_alert WHERE patient_id = :id";
    private static final String LISTENING = """
            SELECT COUNT(*) AS total FROM pg_stat_activity WHERE query = 'LISTEN medical_alert'""";

    private static ConnectionFactory connectionFactory;
    private static DatabaseClient client;
    private static TransactionalOperator transactions;
    private static PostgresPatientRepository patients;
    private static AlertChangeListener listener;
    private static PostgresMedicalAlertRepository alerts;
    private static UUID backfilledPatient;

    @BeforeAll
    static void startTheListener() {
        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        flyway.target("4").load().migrate();
        connectionFactory = connectionFactory();
        client = DatabaseClient.create(connectionFactory);
        transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        // Escalated before the alerts existed: the migration that creates them has to raise its alert
        patients = new PostgresPatientRepository(client, transactions, new OutboxWriter(client),
                new AlertWriter(client) {
                    @Override
                    public Mono<Void> recordFor(PatientChange applied) {
                        return Mono.empty();
                    }
                });
        backfilledPatient = escalate(register(TriageLevel.URGENT, 30)).getId();
        flyway.target("latest").load().migrate();
        patients = new PostgresPatientRepository(client, transactions, new OutboxWriter(client),
                new AlertWriter(client));

        listener = new AlertChangeListener(connectionFactory(), client, Clock.systemUTC(),
                new AlertChangeListener.ReconnectBackoff(Duration.ofMillis(100), Duration.ofMillis(500)));
        alerts = new PostgresMedicalAlertRepository(client, transactions, listener);
        listener.start();
        awaitListening();
    }

    @AfterAll
    static void stopTheListener() {
        listener.stop();
    }

    @Test
    void shouldRaiseAndAnnounceOnePendingAlertWhenAPatientIsEscalated() {
        var patient = register(TriageLevel.CRITICAL, 10);

        StepVerifier.create(changesOf(patient.getId()).next())
                .then(() -> escalate(patient))
                .assertNext(alert -> {
                    assertThat(alert.getStatus()).isEqualTo(AlertStatus.PENDING);
                    assertThat(alert.getTriageLevel()).isEqualTo(TriageLevel.CRITICAL);
                    assertThat(alert.getSlaMinutes()).isEqualTo(5);
                    assertThat(alert.getRegisteredAt()).isEqualTo(patient.getRegisteredAt());
                    assertThat(alert.minutesWaitedAtEscalation()).isEqualTo(10);
                    assertThat(alert.getUpdatedAt()).isEqualTo(alert.getEscalatedAt());
                    assertThat(alert.getAcknowledgedBy()).isNull();
                })
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void shouldRaiseASingleAlertWhenTwoReplicasEscalateAtOnce() {
        var patient = register(TriageLevel.CRITICAL, 10);
        var now = now();

        StepVerifier.create(Flux.merge(
                        patients.escalate(PatientChange.escalation(patient, now)),
                        patients.escalate(PatientChange.escalation(patient, now))))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(countAlerts(patient.getId())).expectNext(1L).verifyComplete();
    }

    @Test
    void shouldResolveTheAlertWhenThePatientEntersTheConsultation() {
        var patient = escalate(register(TriageLevel.URGENT, 20));
        var at = now();

        // The announcement of the escalation itself may still be on its way: wait for the resolution
        StepVerifier.create(changesOf(patient.getId(), AlertStatus.RESOLVED).next())
                .then(() -> move(patient, PatientStatus.IN_CONSULTATION, at))
                .assertNext(alert -> {
                    assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
                    assertThat(alert.getResolvedAt()).isEqualTo(at);
                    assertThat(alert.getUpdatedAt()).isEqualTo(at);
                })
                .expectComplete()
                .verify(WAIT);
    }

    @Test
    void shouldResolveTheAlertWhenThePatientIsDischargedDirectly() {
        var patient = escalate(register(TriageLevel.URGENT_2, 40));
        move(patient, PatientStatus.DISCHARGED, now());

        StepVerifier.create(alertOf(patient.getId()))
                .assertNext(alert -> assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED))
                .verifyComplete();
    }

    @Test
    void shouldNotRaiseAnAlertForAPatientWhoWasNeverEscalated() {
        var patient = register(TriageLevel.NON_URGENT, 5);
        move(patient, PatientStatus.IN_CONSULTATION, now());

        StepVerifier.create(countAlerts(patient.getId())).expectNext(0L).verifyComplete();
    }

    @Test
    void shouldAcknowledgeAPendingAlertOnlyOnce() {
        var patient = escalate(register(TriageLevel.CRITICAL, 10));
        var alertId = idOf(patient.getId());
        var at = now();

        StepVerifier.create(changesOf(patient.getId(), AlertStatus.ACKNOWLEDGED).next())
                .then(() -> StepVerifier.create(alerts.acknowledge(alertId, "Dra. Rojas", at))
                        .assertNext(alert -> {
                            assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
                            assertThat(alert.getAcknowledgedBy()).isEqualTo("Dra. Rojas");
                            assertThat(alert.getAcknowledgedAt()).isEqualTo(at);
                        })
                        .verifyComplete())
                .assertNext(announced -> assertThat(announced.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED))
                .expectComplete()
                .verify(WAIT);

        StepVerifier.create(alerts.acknowledge(alertId, "Dr. Gómez", now())).verifyComplete();
        StepVerifier.create(alerts.findById(alertId))
                .assertNext(alert -> assertThat(alert.getAcknowledgedBy()).isEqualTo("Dra. Rojas"))
                .verifyComplete();
    }

    @Test
    void shouldNotAcknowledgeAResolvedOrUnknownAlert() {
        var patient = escalate(register(TriageLevel.CRITICAL, 10));
        move(patient, PatientStatus.IN_CONSULTATION, now());

        StepVerifier.create(alerts.acknowledge(idOf(patient.getId()), "Dra. Rojas", now())).verifyComplete();
        StepVerifier.create(alerts.acknowledge(Long.MAX_VALUE, "Dra. Rojas", now())).verifyComplete();
        StepVerifier.create(alerts.findById(Long.MAX_VALUE)).verifyComplete();
    }

    @Test
    void shouldListTheActiveAlertsTheMostSevereFirst() {
        var urgent = escalate(register(TriageLevel.URGENT, 30)).getId();
        var critical = escalate(register(TriageLevel.CRITICAL, 10)).getId();
        var resolved = escalate(register(TriageLevel.CRITICAL, 10));
        move(resolved, PatientStatus.DISCHARGED, now());

        StepVerifier.create(alerts.findActive().map(MedicalAlert::getPatientId).collectList())
                .assertNext(active -> {
                    assertThat(active).doesNotContain(resolved.getId());
                    assertThat(active.indexOf(critical)).isLessThan(active.indexOf(urgent));
                })
                .verifyComplete();
    }

    @Test
    void shouldNeitherStoreNorAnnounceTheAlertOfAnEscalationRolledBack() {
        var patient = register(TriageLevel.CRITICAL, 10);
        var failed = patients.escalate(PatientChange.escalation(patient, now()))
                .then(Mono.error(new IllegalStateException("the outer transaction fails")))
                .as(transactions::transactional);

        StepVerifier.create(changesOf(patient.getId()).take(Duration.ofSeconds(1)))
                .then(() -> StepVerifier.create(failed).verifyError(IllegalStateException.class))
                .expectComplete()
                .verify(WAIT);
        StepVerifier.create(countAlerts(patient.getId())).expectNext(0L).verifyComplete();
    }

    @Test
    void shouldRaiseTheAlertsOfThePatientsEscalatedBeforeTheAlertsExisted() {
        StepVerifier.create(alertOf(backfilledPatient))
                .assertNext(alert -> {
                    assertThat(alert.getStatus()).isEqualTo(AlertStatus.PENDING);
                    assertThat(alert.getTriageLevel()).isEqualTo(TriageLevel.URGENT);
                    assertThat(alert.minutesWaitedAtEscalation()).isEqualTo(30);
                })
                .verifyComplete();
    }

    @Test
    void shouldKeepAnnouncingTheChangesAfterLosingItsConnection() {
        var patient = escalate(register(TriageLevel.CRITICAL, 10));
        var alertId = idOf(patient.getId());

        // The change happens while the listener reconnects: it arrives by notification or by the catch-up
        StepVerifier.create(changesOf(patient.getId(), AlertStatus.ACKNOWLEDGED).next())
                .then(() -> {
                    terminateTheListenerConnection();
                    StepVerifier.create(alerts.acknowledge(alertId, "Dra. Rojas", now())).expectNextCount(1)
                            .verifyComplete();
                })
                .expectNextCount(1)
                .expectComplete()
                .verify(WAIT);
        awaitListening();
        assertThat(listener.isRunning()).isTrue();
    }

    private static Flux<MedicalAlert> changesOf(UUID patientId) {
        return alerts.changes().filter(alert -> alert.getPatientId().equals(patientId));
    }

    private static Flux<MedicalAlert> changesOf(UUID patientId, AlertStatus status) {
        return changesOf(patientId).filter(alert -> alert.getStatus() == status);
    }

    private static Mono<MedicalAlert> alertOf(UUID patientId) {
        return client.sql("SELECT id FROM medical_alert WHERE patient_id = :id")
                .bind("id", patientId)
                .map(row -> row.get("id", Long.class))
                .one()
                .flatMap(alerts::findById);
    }

    private static long idOf(UUID patientId) {
        var id = new long[1];
        StepVerifier.create(alertOf(patientId)).consumeNextWith(alert -> id[0] = alert.getId()).verifyComplete();
        return id[0];
    }

    private static Mono<Long> countAlerts(UUID patientId) {
        return client.sql(COUNT_ALERTS).bind("id", patientId).map(row -> row.get("total", Long.class)).one();
    }

    private static Patient register(TriageLevel level, long minutesAgo) {
        var registeredAt = now().minus(Duration.ofMinutes(minutesAgo));
        var patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Paciente de alertas")
                .age(50)
                .vitalSigns(VitalSigns.builder().heartRate(80).systolicPressure(120).diastolicPressure(80)
                        .oxygenSaturation(98).temperature(new BigDecimal("36.5")).painLevel(2).glasgowComaScale(15)
                        .build())
                .chiefComplaint("Control")
                .triageLevel(level)
                .slaMinutes(5)
                .status(PatientStatus.REGISTERED)
                .escalated(false)
                .registeredAt(registeredAt)
                .statusChangedAt(registeredAt)
                .build();
        StepVerifier.create(patients.register(PatientChange.registration(patient))).expectNextCount(1).verifyComplete();
        return patient;
    }

    /** Escalated now: the patient waited the minutes it was registered ago. */
    private static Patient escalate(Patient patient) {
        var escalated = new Patient[1];
        StepVerifier.create(patients.escalate(PatientChange.escalation(patient, now())))
                .consumeNextWith(stored -> escalated[0] = stored)
                .verifyComplete();
        return escalated[0];
    }

    private static void move(Patient patient, PatientStatus next, Instant at) {
        StepVerifier.create(patients.update(PatientChange.transition(patient, next, at)))
                .expectNextCount(1)
                .verifyComplete();
    }

    private static void terminateTheListenerConnection() {
        StepVerifier.create(client.sql("""
                        SELECT pg_terminate_backend(pid) AS terminated
                          FROM pg_stat_activity
                         WHERE query = 'LISTEN medical_alert'""")
                        .map(row -> row.get("terminated", Boolean.class))
                        .all())
                .expectNext(Boolean.TRUE)
                .verifyComplete();
    }

    private static void awaitListening() {
        StepVerifier.create(client.sql(LISTENING).map(row -> row.get("total", Long.class)).one()
                        .filter(total -> total == 1)
                        .repeatWhenEmpty(rounds -> rounds.delayElements(Duration.ofMillis(100)).take(100)))
                .expectNext(1L)
                .verifyComplete();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static ConnectionFactory connectionFactory() {
        return new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build());
    }
}
