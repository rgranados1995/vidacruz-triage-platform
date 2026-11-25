package com.vidacruz.triage.scheduler;

import com.vidacruz.triage.usecase.escalateoverduesla.EscalateOverdueSlaUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * SLA monitor of RF-03: every {@code sla-monitor.interval} escalates the registered patients whose SLA passed.
 */
@Component
@EnableConfigurationProperties(SlaMonitorScheduleProperties.class)
public class SlaMonitorJob extends PeriodicJob {

    private final EscalateOverdueSlaUseCase escalateOverdueSla;

    public SlaMonitorJob(EscalateOverdueSlaUseCase escalateOverdueSla, SlaMonitorScheduleProperties properties) {
        super("SLA monitor", properties.interval());
        this.escalateOverdueSla = escalateOverdueSla;
    }

    @Override
    protected Mono<Long> runRound() {
        return escalateOverdueSla.escalateOverdue();
    }

    /** An escalation is clinically relevant: logged at INFO (the patient ids are in the audit and the events). */
    @Override
    protected void logRound(long processed) {
        log.atInfo().addKeyValue("escalated", processed).log("Patients escalated for exceeding their SLA");
    }
}
