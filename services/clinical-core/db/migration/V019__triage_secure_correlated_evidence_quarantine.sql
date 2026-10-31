-- SYNQORIA M03. Fail-closed QUARANTINE; no application access granted.
-- Binds each replay receipt and outbox row to the exact append-only event digest.
ALTER TABLE synqoria_triage_secure_events_quarantine
 ADD CONSTRAINT triage_secure_event_correlated_identity_quarantine
 UNIQUE (tenant_id,facility_id,patient_id,case_id,command_id,revision,event_digest);
ALTER TABLE synqoria_triage_secure_events_quarantine
 ADD CONSTRAINT triage_secure_genesis_operation_quarantine CHECK (revision <> 1 OR operation = 'ARRIVAL');
ALTER TABLE synqoria_triage_secure_events_quarantine
 ADD CONSTRAINT triage_secure_genesis_previous_quarantine CHECK
 (revision <> 1 OR previous_digest = repeat('0',64));
ALTER TABLE synqoria_triage_secure_receipts_quarantine
 ADD CONSTRAINT triage_secure_receipt_exact_event_fk_quarantine
 FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id,revision,event_digest)
 REFERENCES synqoria_triage_secure_events_quarantine
 (tenant_id,facility_id,patient_id,case_id,command_id,revision,event_digest);
-- A separate composite key is necessary for the outbox, which omits revision.
ALTER TABLE synqoria_triage_secure_events_quarantine
 ADD CONSTRAINT triage_secure_event_outbox_key_quarantine
 UNIQUE (tenant_id,facility_id,patient_id,case_id,command_id,event_digest);
ALTER TABLE synqoria_triage_secure_outbox_quarantine
 ADD CONSTRAINT triage_secure_outbox_exact_event_fk_quarantine
 FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id,event_digest)
 REFERENCES synqoria_triage_secure_events_quarantine
 (tenant_id,facility_id,patient_id,case_id,command_id,event_digest);
-- No deletion or update of committed replay evidence. Requires separate reviewed dispatcher design.
CREATE TRIGGER triage_secure_receipts_append_only_quarantine
 BEFORE UPDATE OR DELETE ON synqoria_triage_secure_receipts_quarantine
 FOR EACH ROW EXECUTE FUNCTION synqoria_triage_secure_event_append_only_quarantine();
ALTER TABLE synqoria_triage_secure_events_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_events_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_receipts_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_receipts_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_outbox_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_outbox_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_secure_events_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_triage_secure_receipts_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_triage_secure_outbox_quarantine FROM PUBLIC;
-- No CREATE POLICY, GRANT, BYPASSRLS or restricted runtime role here.
