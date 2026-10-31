import sys, pathlib, tempfile, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from triage_migration_gate import audit
SOURCE=ROOT/'services/clinical-core/db/migration/V015__triage_workflow_quarantine.sql'
class TriageMigrationGateTests(unittest.TestCase):
    def inspect(self, sql):
        with tempfile.TemporaryDirectory() as d:
            p=pathlib.Path(d)/'V015.sql';p.write_text(sql)
            return audit(p)
    def test_valid_quarantine(self):self.assertEqual(audit(SOURCE)['status'],'source_quarantine_verified_only')
    def test_public_grant_denied(self):
        with self.assertRaises(ValueError):self.inspect(SOURCE.read_text()+'\nGRANT SELECT ON synqoria_triage_cases_quarantine TO PUBLIC;')
    def test_rls_removed_denied(self):
        with self.assertRaises(ValueError):self.inspect(SOURCE.read_text().replace('ALTER TABLE synqoria_triage_cases_quarantine FORCE ROW LEVEL SECURITY;',''))
    def test_patient_scope_removed_denied(self):
        with self.assertRaises(ValueError):self.inspect(SOURCE.read_text().replace('patient_id uuid NOT NULL,','patient_id uuid,',1))
    def test_unreviewed_table_denied(self):
        with self.assertRaises(ValueError):self.inspect(SOURCE.read_text()+'\nCREATE TABLE unsafe_exposure (id uuid);')
    def test_outbox_evidence_removed_denied(self):
        with self.assertRaises(ValueError):self.inspect(SOURCE.read_text().replace('triage_outbox_event_unique','outbox_unprotected'))
    def test_policy_creation_denied(self):
        with self.assertRaises(ValueError):self.inspect(SOURCE.read_text()+'\nCREATE POLICY open_all ON synqoria_triage_cases_quarantine USING (true);')
if __name__=='__main__':unittest.main()
