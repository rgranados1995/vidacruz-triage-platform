-- Alerts to the medical team (RF-06): one per escalated patient, created in the transaction of the escalation and
-- resolved in the transaction that takes the patient to the consultation or discharges it.
CREATE TABLE medical_alert (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id      UUID         NOT NULL UNIQUE REFERENCES patient (id),
    triage_level    VARCHAR(20)  NOT NULL
        CHECK (triage_level IN ('CRITICAL', 'URGENT', 'URGENT_2', 'NON_URGENT', 'NON_URGENT_2')),
    sla_minutes     INTEGER      NOT NULL,
    registered_at   TIMESTAMPTZ  NOT NULL,
    escalated_at    TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'ACKNOWLEDGED', 'RESOLVED')),
    acknowledged_by VARCHAR(100),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL
);

-- Active alerts are read on every connection to the stream; changes since a moment, when the listener reconnects
CREATE INDEX medical_alert_active_idx ON medical_alert (status) WHERE status <> 'RESOLVED';
CREATE INDEX medical_alert_updated_idx ON medical_alert (updated_at);

-- Patients escalated before the alerts existed and still waiting get their alert now
INSERT INTO medical_alert (patient_id, triage_level, sla_minutes, registered_at, escalated_at, status, updated_at)
SELECT p.id, p.triage_level, p.sla_minutes, p.registered_at, escalation.occurred_at, 'PENDING', escalation.occurred_at
  FROM patient p
  JOIN LATERAL (SELECT a.occurred_at
                  FROM clinical_audit a
                 WHERE a.patient_id = p.id
                   AND a.event_type = 'ESCALATED'
                 ORDER BY a.occurred_at
                 LIMIT 1) AS escalation ON TRUE
 WHERE p.escalated
   AND p.status = 'REGISTERED';
