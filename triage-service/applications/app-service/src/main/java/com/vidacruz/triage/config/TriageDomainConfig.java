package com.vidacruz.triage.config;

import com.vidacruz.triage.model.triage.AdministrativeComplaints;
import com.vidacruz.triage.model.triage.SlaPolicy;
import com.vidacruz.triage.model.triage.TriageClassifier;
import com.vidacruz.triage.model.validation.PatientDataValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Domain services used by the use cases. The domain has no framework annotations, so they are created here.
 */
@Configuration
@EnableConfigurationProperties(TriageProperties.class)
public class TriageDomainConfig {

    /** UTC clock: every instant is stored and published in UTC. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
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
}
