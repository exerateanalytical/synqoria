-- M03 internal encrypted persistence extension. QUARANTINE ONLY; NOT DEPLOYMENT APPROVAL.
-- RLS is still deny-all: there are NO policies or application role grants.
-- KMS/key rotation, DB restricted roles, backup encryption and live PostgreSQL tests remain blocked.
ALTER TABLE synqoria_triage_cases_quarantine
  ADD COLUMN IF NOT EXISTS snapshot_ciphertext bytea,
  ADD COLUMN IF NOT EXISTS snapshot_key_id text,
  ADD COLUMN IF NOT EXISTS snapshot_digest text;
ALTER TABLE synqoria_triage_cases_quarantine
  ADD CONSTRAINT triage_snapshot_key_format_quarantine CHECK (snapshot_key_id IS NULL OR snapshot_key_id ~ '^[A-Za-z0-9._-]{1,80}$'),
  ADD CONSTRAINT triage_snapshot_digest_format_quarantine CHECK (snapshot_digest IS NULL OR snapshot_digest ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT triage_snapshot_size_quarantine CHECK (snapshot_ciphertext IS NULL OR octet_length(snapshot_ciphertext) BETWEEN 29 AND 4200000),
  ADD CONSTRAINT triage_snapshot_atomic_null_quarantine CHECK (
     (snapshot_ciphertext IS NULL AND snapshot_key_id IS NULL AND snapshot_digest IS NULL)
     OR (snapshot_ciphertext IS NOT NULL AND snapshot_key_id IS NOT NULL AND snapshot_digest IS NOT NULL));
-- Legacy rows without encrypted snapshots are not readable by the new adapter (fail closed).
CREATE OR REPLACE FUNCTION synqoria_triage_quarantine_append_only()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'triage audit and outbox records are append-only';
END;
$$;
CREATE TRIGGER triage_events_append_only_quarantine BEFORE UPDATE OR DELETE ON synqoria_triage_events_quarantine
FOR EACH ROW EXECUTE FUNCTION synqoria_triage_quarantine_append_only();
CREATE TRIGGER triage_outbox_append_only_quarantine BEFORE UPDATE OR DELETE ON synqoria_triage_outbox_quarantine
FOR EACH ROW EXECUTE FUNCTION synqoria_triage_quarantine_append_only();
ALTER TABLE synqoria_triage_cases_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_cases_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_events_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_events_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_outbox_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_outbox_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_cases_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_triage_events_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_triage_outbox_quarantine FROM PUBLIC;
-- Intentionally no CREATE POLICY or GRANT; no restricted role can yet use this migration.
