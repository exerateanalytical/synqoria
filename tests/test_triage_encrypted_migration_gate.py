import sys,pathlib,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from triage_encrypted_migration_gate import audit
SOURCE=ROOT/'services/clinical-core/db/migration/V016__triage_encrypted_snapshot_quarantine.sql'
class QuarantineEncryptedMigrationTests(unittest.TestCase):
 def audit_change(self,changed):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/'V016.sql';p.write_text(changed);return audit(p)
 def test_source_is_quarantined(self):self.assertEqual(audit(SOURCE)['append_only_triggers'],2)
 def test_public_grant_rejected(self):
  with self.assertRaises(ValueError):self.audit_change(SOURCE.read_text()+'\nGRANT SELECT ON synqoria_triage_cases_quarantine TO PUBLIC;')
 def test_policy_rejected(self):
  with self.assertRaises(ValueError):self.audit_change(SOURCE.read_text()+'\nCREATE POLICY allow_all ON synqoria_triage_cases_quarantine USING (true);')
 def test_missing_force_rls_rejected(self):
  with self.assertRaises(ValueError):self.audit_change(SOURCE.read_text().replace('ALTER TABLE synqoria_triage_cases_quarantine FORCE ROW LEVEL SECURITY;',''))
 def test_missing_encryption_column_rejected(self):
  with self.assertRaises(ValueError):self.audit_change(SOURCE.read_text().replace('snapshot_ciphertext bytea','snapshot_plaintext text'))
 def test_missing_event_trigger_rejected(self):
  with self.assertRaises(ValueError):self.audit_change(SOURCE.read_text().replace('triage_events_append_only_quarantine','triage_events_unprotected'))
 def test_missing_outbox_trigger_rejected(self):
  with self.assertRaises(ValueError):self.audit_change(SOURCE.read_text().replace('triage_outbox_append_only_quarantine','triage_outbox_unprotected'))
 def test_missing_atomic_null_constraint_rejected(self):
  with self.assertRaises(ValueError):self.audit_change(SOURCE.read_text().replace('triage_snapshot_atomic_null_quarantine','triage_snapshot_unprotected'))
 def test_missing_digest_format_rejected(self):
  with self.assertRaises(ValueError):self.audit_change(SOURCE.read_text().replace('triage_snapshot_digest_format_quarantine','triage_snapshot_unchecked'))
if __name__=='__main__':unittest.main()
