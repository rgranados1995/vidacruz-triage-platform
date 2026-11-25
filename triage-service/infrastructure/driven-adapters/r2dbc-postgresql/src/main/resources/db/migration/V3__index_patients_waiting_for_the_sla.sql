-- The SLA monitor (RF-03) only looks at registered patients not escalated yet: a partial index keeps that query
-- small no matter how many patients were attended or discharged before.
CREATE INDEX patient_waiting_sla_idx ON patient (registered_at) WHERE status = 'REGISTERED' AND NOT escalated;
