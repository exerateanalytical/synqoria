-- SYNQORIA M02 QUARANTINE ONLY: independent DBA review required before deployment.
-- No CREATE POLICY, no BYPASSRLS, no application grants. Clinical writes remain disabled.
CREATE TABLE IF NOT EXISTS synqoria_appointment_command_audit_quarantine (
 event_id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 appointment_id uuid NOT NULL,
 actor_pseudonym text NOT NULL CHECK (length(actor_pseudonym) BETWEEN 8 AND 64),
 appointment_revision bigint NOT NULL CHECK (appointment_revision > 0),
 command_digest text NOT NULL CHECK (command_digest ~ '^[0-9a-f]{64}$'),
 previous_digest text NOT NULL CHECK (previous_digest ~ '^[0-9a-f]{64}$'),
 audit_digest text NOT NULL CHECK (audit_digest ~ '^[0-9a-f]{64}$'),
 occurred_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id,facility_id,appointment_id,appointment_revision)
);
CREATE TABLE IF NOT EXISTS synqoria_appointment_outbox_quarantine (
 event_id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 appointment_id uuid NOT NULL,
 appointment_revision bigint NOT NULL CHECK (appointment_revision > 0),
 event_type text NOT NULL CHECK (event_type ~ '^appointment[.][a-z_]{3,40}$'),
 payload_digest text NOT NULL CHECK (payload_digest ~ '^[0-9a-f]{64}$'),
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id,facility_id,appointment_id,appointment_revision,event_type)
);
ALTER TABLE synqoria_appointment_command_audit_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_appointment_command_audit_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_appointment_outbox_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_appointment_outbox_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_appointment_command_audit_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_appointment_outbox_quarantine FROM PUBLIC;
-- The application must not connect using table owner or a BYPASSRLS role.
