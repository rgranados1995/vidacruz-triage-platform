package com.vidacruz.triage.r2dbc.report;

import com.vidacruz.triage.model.patient.Patient;
import com.vidacruz.triage.model.patient.PatientChange;
import com.vidacruz.triage.model.patient.PatientStatus;
import com.vidacruz.triage.model.patient.VitalSigns;
import com.vidacruz.triage.model.report.HourlyOperation;
import com.vidacruz.triage.model.report.OperationReport;
import com.vidacruz.triage.model.report.OperationStats;
import com.vidacruz.triage.model.report.ReportPeriod;
import com.vidacruz.triage.model.triage.TriageLevel;
import com.vidacruz.triage.r2dbc.alert.AlertWriter;
import com.vidacruz.triage.r2dbc.outbox.OutboxWriter;
import com.vidacruz.triage.r2dbc.patient.PostgresPatientRepository;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-05 against a real PostgreSQL. Every expected figure was calculated by hand from this data set (10 March 2020,
 * UTC); the report covers 08:00 to 11:00:
 * <pre>
 * hour  patient  level         registered  first consultation   wait
 * 08    P1       CRITICAL      08:05       08:10 (again 08:40)   5
 * 08    P2       URGENT        08:20       08:50                30
 * 08    P3       NON_URGENT    08:40       09:40                60  (attended in the next hour)
 * 08    P4       NON_URGENT_2  08:45       still waiting         -
 * 08    P5       URGENT_2      08:50       discharged directly   -
 * 09    -
 * 10    P6       CRITICAL      10:00       10:02:30            2.5  (then discharged)
 * out   P7       URGENT        11:00       11:01                     ({@code to} is excluded)
 * out   P8       URGENT        07:59:59    08:30                     (before {@code from})
 * </pre>
 * Hour 08: waits 5, 30, 60 → average 31.67; p95 = 30 + 0.9 × 30 = 57. Whole range: waits 2.5, 5, 30, 60 → average
 * 24.38; p95 = 30 + 0.85 × 30 = 55.5.
 */
@Testcontainers
class PostgresOperationReportRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.16-alpine");

    private static final Instant FROM = instantAt("08:00:00");
    private static final Instant TO = instantAt("11:00:00");
    private static final String SECOND_CONSULTATION_ENTRY = """
            INSERT INTO clinical_audit (patient_id, event_type, previous_status, new_status, occurred_at)
            VALUES (:id, 'CONSULTATION_STARTED', 'REGISTERED', 'IN_CONSULTATION', :at)""";

    private static ConnectionFactory connectionFactory;

    @BeforeAll
    static void storeTheDataSet() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        connectionFactory = connectionFactory(Map.of());
        var client = DatabaseClient.create(connectionFactory);
        var patients = new PostgresPatientRepository(client,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)), new OutboxWriter(client),
                new AlertWriter(client));

        var p1 = store(patients, TriageLevel.CRITICAL, "08:05:00");
        consult(patients, p1, "08:10:00");
        // Only the first entry into the consultation counts, even if the audit had another one
        StepVerifier.create(client.sql(SECOND_CONSULTATION_ENTRY)
                        .bind("id", p1.getId())
                        .bind("at", instantAt("08:40:00"))
                        .fetch().rowsUpdated())
                .expectNext(1L)
                .verifyComplete();
        consult(patients, store(patients, TriageLevel.URGENT, "08:20:00"), "08:50:00");
        consult(patients, store(patients, TriageLevel.NON_URGENT, "08:40:00"), "09:40:00");
        store(patients, TriageLevel.NON_URGENT_2, "08:45:00");
        move(patients, store(patients, TriageLevel.URGENT_2, "08:50:00"), PatientStatus.DISCHARGED, "09:00:00");
        var p6 = consult(patients, store(patients, TriageLevel.CRITICAL, "10:00:00"), "10:02:30");
        move(patients, p6, PatientStatus.DISCHARGED, "10:30:00");
        consult(patients, store(patients, TriageLevel.URGENT, "11:00:00"), "11:01:00");
        consult(patients, store(patients, TriageLevel.URGENT, "07:59:59"), "08:30:00");
    }

    @Test
    void shouldGroupTheRangeByHourIncludingTheHoursWithoutPatients() {
        StepVerifier.create(report(connectionFactory, FROM, TO))
                .assertNext(report -> {
                    assertThat(report.period()).isEqualTo(new ReportPeriod(FROM, TO));
                    assertThat(report.hours()).extracting(HourlyOperation::hour)
                            .containsExactly(instantAt("08:00:00"), instantAt("09:00:00"), instantAt("10:00:00"));

                    var eight = report.hours().getFirst().stats();
                    assertThat(eight.registered()).isEqualTo(5);
                    assertThat(eight.registeredByLevel()).isEqualTo(levels(1, 1, 1, 1, 1));
                    assertThat(eight.attended()).isEqualTo(3);
                    assertThat(eight.averageWaitMinutes()).isEqualByComparingTo("31.67");
                    assertThat(eight.p95WaitMinutes()).isEqualByComparingTo("57");

                    var nine = report.hours().get(1).stats();
                    assertThat(nine.registered()).isZero();
                    assertThat(nine.registeredByLevel()).isEqualTo(levels(0, 0, 0, 0, 0));
                    assertThat(nine.attended()).isZero();
                    assertThat(nine.averageWaitMinutes()).isNull();
                    assertThat(nine.p95WaitMinutes()).isNull();

                    var ten = report.hours().getLast().stats();
                    assertThat(ten.registered()).isEqualTo(1);
                    assertThat(ten.registeredByLevel()).isEqualTo(levels(1, 0, 0, 0, 0));
                    assertThat(ten.averageWaitMinutes()).isEqualByComparingTo("2.5");
                    assertThat(ten.p95WaitMinutes()).isEqualByComparingTo("2.5");
                })
                .verifyComplete();
    }

    @Test
    void shouldComputeTheWholeRangeFromThePatientsAndNotFromTheHours() {
        StepVerifier.create(report(connectionFactory, FROM, TO))
                .assertNext(report -> {
                    OperationStats summary = report.summary();
                    assertThat(summary.registered()).isEqualTo(6);
                    assertThat(summary.registeredByLevel()).isEqualTo(levels(2, 1, 1, 1, 1));
                    assertThat(summary.attended()).isEqualTo(4);
                    assertThat(summary.averageWaitMinutes()).isEqualByComparingTo("24.38");
                    assertThat(summary.p95WaitMinutes()).isEqualByComparingTo("55.5");
                })
                .verifyComplete();
    }

    @Test
    void shouldCountOnlyThePartOfAnHourInsideTheRange() {
        StepVerifier.create(report(connectionFactory, instantAt("08:30:00"), instantAt("10:15:00")))
                .assertNext(report -> {
                    assertThat(report.hours()).extracting(HourlyOperation::hour)
                            .containsExactly(instantAt("08:00:00"), instantAt("09:00:00"), instantAt("10:00:00"));
                    var eight = report.hours().getFirst().stats();
                    assertThat(eight.registered()).isEqualTo(3);
                    assertThat(eight.registeredByLevel()).isEqualTo(levels(0, 0, 1, 1, 1));
                    assertThat(eight.attended()).isEqualTo(1);
                    assertThat(eight.averageWaitMinutes()).isEqualByComparingTo("60");
                    assertThat(report.summary().registered()).isEqualTo(4);
                })
                .verifyComplete();
    }

    @Test
    void shouldGroupByUtcHoursWhateverTheTimeZoneOfTheSession() {
        // India is UTC+05:30: hours truncated in the session zone would start at minute 30
        var india = connectionFactory(Map.of("TimeZone", "Asia/Kolkata"));

        StepVerifier.create(report(india, FROM, TO))
                .assertNext(report -> {
                    assertThat(report.hours()).extracting(HourlyOperation::hour)
                            .containsExactly(instantAt("08:00:00"), instantAt("09:00:00"), instantAt("10:00:00"));
                    assertThat(report.hours().getFirst().stats().registered()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    void shouldReportARangeWithoutPatients() {
        var night = Instant.parse("2020-03-11T02:00:00Z");

        StepVerifier.create(report(connectionFactory, night, night.plusSeconds(1800)))
                .assertNext(report -> {
                    assertThat(report.hours()).singleElement()
                            .satisfies(hour -> assertThat(hour.hour()).isEqualTo(night));
                    assertThat(report.summary().registered()).isZero();
                    assertThat(report.summary().averageWaitMinutes()).isNull();
                })
                .verifyComplete();
    }

    private static Mono<OperationReport> report(ConnectionFactory factory, Instant from, Instant to) {
        return new PostgresOperationReportRepository(DatabaseClient.create(factory))
                .operationReport(new ReportPeriod(from, to));
    }

    /** Counts in the order of the levels: CRITICAL, URGENT, URGENT_2, NON_URGENT, NON_URGENT_2. */
    private static Map<TriageLevel, Long> levels(long... perLevel) {
        var levels = new EnumMap<TriageLevel, Long>(TriageLevel.class);
        for (var level : TriageLevel.values()) {
            levels.put(level, perLevel[level.ordinal()]);
        }
        return levels;
    }

    private static Patient store(PostgresPatientRepository patients, TriageLevel level, String time) {
        var registeredAt = instantAt(time);
        var patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("Paciente del reporte")
                .age(40)
                .vitalSigns(VitalSigns.builder().heartRate(80).systolicPressure(120).diastolicPressure(80)
                        .oxygenSaturation(98).temperature(new BigDecimal("36.5")).painLevel(2).glasgowComaScale(15)
                        .build())
                .chiefComplaint("Control")
                .triageLevel(level)
                .slaMinutes(60)
                .status(PatientStatus.REGISTERED)
                .escalated(false)
                .registeredAt(registeredAt)
                .statusChangedAt(registeredAt)
                .build();
        StepVerifier.create(patients.register(PatientChange.registration(patient))).expectNextCount(1).verifyComplete();
        return patient;
    }

    private static Patient consult(PostgresPatientRepository patients, Patient patient, String time) {
        return move(patients, patient, PatientStatus.IN_CONSULTATION, time);
    }

    private static Patient move(PostgresPatientRepository patients, Patient patient, PatientStatus next,
                                String time) {
        var moved = new Patient[1];
        StepVerifier.create(patients.update(PatientChange.transition(patient, next, instantAt(time))))
                .consumeNextWith(stored -> moved[0] = stored)
                .verifyComplete();
        return moved[0];
    }

    private static Instant instantAt(String time) {
        return Instant.parse("2020-03-10T" + time + "Z");
    }

    private static ConnectionFactory connectionFactory(Map<String, String> options) {
        return new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .options(options)
                .build());
    }
}
