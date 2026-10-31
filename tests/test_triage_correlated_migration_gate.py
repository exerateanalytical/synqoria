"""Adversarial V019 static migration tests; cannot certify live database behavior."""
import sys, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from triage_correlated_migration_gate import SQL,audit

class CorrelationGateTests(unittest.TestCase):
    def test_clean_migration(self):
        self.assertTrue(audit(SQL.read_text())['passed'])

    def test_unsafe_grant_denied(self):
        self.assertFalse(audit(SQL.read_text()+ '\nGRANT ALL ON synqoria_triage_secure_events_quarantine TO PUBLIC;')['passed'])

    def test_bypass_rls_denied(self):
        self.assertFalse(audit(SQL.read_text()+ '\nALTER ROLE app BYPASSRLS;')['passed'])

    def test_unsafe_policy_denied(self):
        self.assertFalse(audit(SQL.read_text()+ '\nCREATE POLICY allow_everyone ON foo USING (true);')['passed'])

    def test_forged_receipt_fk_denied(self):
        source=SQL.read_text()
        source=source.replace('FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id,revision,event_digest)',
                              'FOREIGN KEY (command_id,revision,event_digest)')
        self.assertFalse(audit(source)['passed'])

    def test_forged_outbox_fk_denied(self):
        source=SQL.read_text().replace('FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id,event_digest)',
                                'FOREIGN KEY (command_id,event_digest)')
        self.assertFalse(audit(source)['passed'])

    def test_missing_receipt_append_only_denied(self):
        source=SQL.read_text().replace('BEFORE UPDATE OR DELETE ON synqoria_triage_secure_receipts_quarantine',
                                      'BEFORE INSERT ON synqoria_triage_secure_receipts_quarantine')
        self.assertFalse(audit(source)['passed'])

    def test_missing_force_rls_denied(self):
        source=SQL.read_text().replace('ALTER TABLE synqoria_triage_secure_events_quarantine FORCE ROW LEVEL SECURITY;', '')
        self.assertFalse(audit(source)['passed'])

    def test_missing_revoked_public_denied(self):
        source=SQL.read_text().replace('REVOKE ALL ON synqoria_triage_secure_receipts_quarantine FROM PUBLIC;', '')
        self.assertFalse(audit(source)['passed'])

    def test_genesis_only_operation_rule(self):
        source=SQL.read_text().replace('triage_secure_genesis_operation_quarantine', 'removed_genesis_constraint')
        self.assertFalse(audit(source)['passed'])

    def test_genesis_anchor_rule(self):
        source=SQL.read_text().replace('triage_secure_genesis_previous_quarantine','removed_prev_constraint')
        self.assertFalse(audit(source)['passed'])

    def test_no_drop_table(self):
        self.assertFalse(audit(SQL.read_text()+'\nDROP TABLE synqoria_triage_secure_events_quarantine;')['passed'])

if __name__=='__main__':unittest.main()
