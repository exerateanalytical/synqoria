-- M03 P06: genesis event / receipt referential integrity. QUARANTINE ONLY, NO GRANTS.
-- V017 required revision>1 because initial intake was not event-backed. This repairs the gap.
ALTER TABLE synqoria_triage_secure_events_quarantine
 DROP CONSTRAINT IF EXISTS synqoria_triage_secure_events_quarantine_revision_check;
ALTER TABLE synqoria_triage_secure_events_quarantine
 ADD CONSTRAINT triage_secure_event_revision_quarantine CHECK (revision >= 1);
-- Receipt must reference the exact scoped event; inserts must occur in event -> receipt order.
ALTER TABLE synqoria_triage_secure_receipts_quarantine
 ADD CONSTRAINT triage_secure_receipt_event_fk_quarantine
 FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id)
 REFERENCES synqoria_triage_secure_events_quarantine (tenant_id,facility_id,patient_id,case_id,command_id);
-- Prevent clinical audit event tampering; no update or deletion is supported.
CREATE OR REPLACE FUNCTION synqoria_triage_secure_event_append_only_quarantine()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'triage audit events are append-only';
END;
$$;
CREATE TRIGGER triage_secure_events_append_only_quarantine
 BEFORE UPDATE OR DELETE ON synqoria_triage_secure_events_quarantine
 FOR EACH ROW EXECUTE FUNCTION synqoria_triage_secure_event_append_only_quarantine();
-- Outbox delivery metadata is intentionally NOT granted here. Dispatcher policy requires review.
ALTER TABLE synqoria_triage_secure_events_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_events_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_receipts_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_receipts_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_secure_events_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_triage_secure_receipts_quarantine FROM PUBLIC;
-- No CREATE POLICY / GRANT / BYPASSRLS. This migration cannot enable a clinical deployment.
