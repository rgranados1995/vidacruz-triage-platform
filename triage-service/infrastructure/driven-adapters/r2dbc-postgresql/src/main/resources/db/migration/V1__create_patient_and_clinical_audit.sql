-- Patients of the emergency room (RF-01, RF-02). Enum values are the domain names; the API maps them to the contract.
CREATE TABLE patient (
    id                      UUID         PRIMARY KEY,
    full_name               VARCHAR(100) NOT NULL,
    age                     INTEGER      NOT NULL,
    heart_rate              INTEGER      NOT NULL,
    systolic_pressure       INTEGER      NOT NULL,
    diastolic_pressure      INTEGER      NOT NULL,
    oxygen_saturation       INTEGER      NOT NULL,
    temperature             NUMERIC      NOT NULL,
    pain_level              INTEGER      NOT NULL,
    glasgow_coma_scale      INTEGER      NOT NULL,
    chief_complaint         VARCHAR(500) NOT NULL,
    triage_level            VARCHAR(20)  NOT NULL
        CHECK (triage_level IN ('CRITICAL', 'URGENT', 'URGENT_2', 'NON_URGENT', 'NON_URGENT_2')),
    sla_minutes             INTEGER      NOT NULL CHECK (sla_minutes > 0),
    status                  VARCHAR(20)  NOT NULL CHECK (status IN ('REGISTERED', 'IN_CONSULTATION', 'DISCHARGED')),
    escalated               BOOLEAN      NOT NULL DEFAULT FALSE,
    registered_at           TIMESTAMPTZ  NOT NULL,
    status_changed_at       TIMESTAMPTZ  NOT NULL,
    consultation_started_at TIMESTAMPTZ
);

-- Clinical audit of RN-04: one row per change of status or flag.
CREATE TABLE clinical_audit (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id      UUID        NOT NULL REFERENCES patient (id),
    event_type      VARCHAR(30) NOT NULL
        CHECK (event_type IN ('REGISTERED', 'CONSULTATION_STARTED', 'DISCHARGED', 'ESCALATED')),
    previous_status VARCHAR(20),
    new_status      VARCHAR(20) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX clinical_audit_patient_idx ON clinical_audit (patient_id, occurred_at);

-- The audit is append-only even for clients that bypass the API.
CREATE FUNCTION reject_clinical_audit_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'clinical_audit is append-only: % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER clinical_audit_append_only
    BEFORE UPDATE OR DELETE ON clinical_audit
    FOR EACH ROW EXECUTE FUNCTION reject_clinical_audit_change();

CREATE TRIGGER clinical_audit_no_truncate
    BEFORE TRUNCATE ON clinical_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_clinical_audit_change();
