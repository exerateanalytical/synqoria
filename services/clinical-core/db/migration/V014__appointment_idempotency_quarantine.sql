-- P06 M02 draft. QUARANTINE ONLY: NO APPLICATION GRANTS, NO RLS POLICY, NO DEPLOYMENT AUTHORIZATION.
-- Idempotency key stored only as SHA-256; command digest excludes raw clinical text.
CREATE TABLE IF NOT EXISTS synqoria_appointment_idempotency_quarantine (
    tenant_id uuid NOT NULL,
    facility_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    key_digest text NOT NULL CHECK (key_digest ~ '^[0-9a-f]{64}$'),
    command_digest text NOT NULL CHECK (command_digest ~ '^[0-9a-f]{64}$'),
    state text NOT NULL CHECK (state IN ('PENDING','COMMITTED')),
    appointment_id uuid NOT NULL,
    result_revision bigint CHECK (result_revision > 0),
    event_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT appointment_idempotency_unique UNIQUE (tenant_id,facility_id,actor_id,key_digest),
    CONSTRAINT appointment_idempotency_outcome CHECK (
        (state = 'PENDING' AND result_revision IS NULL AND event_id IS NULL) OR
        (state = 'COMMITTED' AND result_revision IS NOT NULL AND event_id IS NOT NULL)
    )
);
ALTER TABLE synqoria_appointment_idempotency_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_appointment_idempotency_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_appointment_idempotency_quarantine FROM PUBLIC;
-- No CREATE POLICY, no BYPASSRLS, no application grants. A restricted-role migration review is mandatory.
