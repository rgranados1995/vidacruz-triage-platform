package com.vidacruz.triage.config;

import com.vidacruz.triage.model.outbox.OutboxRelayPolicy;
import com.vidacruz.triage.model.report.ReportPolicy;
import com.vidacruz.triage.model.triage.AdministrativeComplaints;
import com.vidacruz.triage.model.triage.SlaMonitorPolicy;
import com.vidacruz.triage.model.triage.SlaPolicy;
import com.vidacruz.triage.model.triage.TriageClassifier;
import com.vidacruz.triage.model.validation.PatientDataValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Domain services used by the use cases. The domain has no framework annotations, so they are created here.
 */
@Configuration
@EnableConfigurationProperties({TriageProperties.class, OutboxProperties.class, SlaMonitorProperties.class,
    ReportProperties.class})
public class TriageDomainConfig {

    /**
     * UTC clock with microsecond precision, the precision PostgreSQL stores: an instant answered or published right
     * after a change is the same one read back later.
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }

    @Bean
    public TriageClassifier triageClassifier(TriageProperties properties) {
        return new TriageClassifier(new AdministrativeComplaints(properties.administrativeComplaints()));
    }

    @Bean
    public SlaPolicy slaPolicy(TriageProperties properties) {
        return new SlaPolicy(properties.slaMinutes());
    }

    @Bean
    public PatientDataValidator patientDataValidator() {
        return new PatientDataValidator();
    }

    @Bean
    public SlaMonitorPolicy slaMonitorPolicy(SlaMonitorProperties properties) {
        return new SlaMonitorPolicy(properties.batchSize());
    }

    @Bean
    public ReportPolicy reportPolicy(ReportProperties properties) {
        return new ReportPolicy(properties.maxRange());
    }

    @Bean
    public OutboxRelayPolicy outboxRelayPolicy(OutboxProperties properties) {
        return new OutboxRelayPolicy(properties.batchSize(), properties.lease());
    }
}
