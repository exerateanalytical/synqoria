-- SYNQORIA P03-B0043 — pseudonymous, append-only identity security audit.
-- Identity-only database, not a general clinical event or patient audit log.
-- Neither direct identifiers nor bearer tokens, names, IPs, email or free text
-- are allowed. Retention policies and qualified external audit remain gated.
BEGIN;

CREATE TABLE IF NOT EXISTS synqoria_identity.staff_security_audit (
 event_id uuid PRIMARY KEY,
 actor_digest char(64) NOT NULL,
 tenant_digest char(64) NOT NULL,
 kind varchar(48) NOT NULL,
 outcome varchar(12) NOT NULL,
 correlation_id uuid NOT NULL,
 occurred_at timestamptz NOT NULL,
 stored_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 CONSTRAINT identity_audit_actor_hash CHECK (actor_digest ~ '^[0-9a-f]{64}$'),
 CONSTRAINT identity_audit_tenant_hash CHECK (tenant_digest ~ '^[0-9a-f]{64}$'),
 CONSTRAINT identity_audit_kind CHECK (kind IN (
  'LOGIN_GRANTED','LOGIN_DENIED','SESSION_REVOKED','SESSION_EXPIRED',
  'WORKFORCE_GRANT_DENIED','PATIENT_DISCLOSURE_DENIED','ENCOUNTER_DISCLOSURE_DENIED')),
 CONSTRAINT identity_audit_outcome CHECK (outcome IN ('PERMITTED','DENIED'))
);
CREATE INDEX IF NOT EXISTS identity_audit_occurrence_idx
 ON synqoria_identity.staff_security_audit(occurred_at);

DO $roles$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_identity_audit_writer') THEN
  CREATE ROLE synqoria_identity_audit_writer NOLOGIN NOINHERIT NOBYPASSRLS;
 END IF;
END
$roles$;
REVOKE ALL ON TABLE synqoria_identity.staff_security_audit FROM PUBLIC;
GRANT USAGE ON SCHEMA synqoria_identity TO synqoria_identity_audit_writer;
GRANT INSERT ON TABLE synqoria_identity.staff_security_audit
 TO synqoria_identity_audit_writer;

ALTER TABLE synqoria_identity.staff_security_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_identity.staff_security_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY identity_security_audit_append_only
 ON synqoria_identity.staff_security_audit FOR INSERT TO synqoria_identity_audit_writer
 WITH CHECK (occurred_at <= clock_timestamp() + INTERVAL '1 minute'
 AND occurred_at >= clock_timestamp() - INTERVAL '2 minutes');

-- No SELECT, UPDATE or DELETE policies for the runtime writer.
-- Qualified audit reading/retention is a separate deployment role and
-- explicitly not an implicit BYPASSRLS permission for clinical staff.
COMMIT;
