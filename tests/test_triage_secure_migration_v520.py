import unittest,tempfile,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from triage_secure_migration_gate import audit
SQL=Path(__file__).resolve().parents[1]/'services/clinical-core/db/migration/V017__triage_secure_receipts_quarantine.sql'
class Tests(unittest.TestCase):
 def setUp(self):self.sql=SQL.read_text()
 def check(self,source,expected):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'V016.sql';p.write_text(source)
   r=audit(p)
   self.assertFalse(r['ok']);self.assertTrue(any(expected in x for x in r['violations']),r)
 def test_authoritative_quarantine(self):self.assertTrue(audit(SQL)['ok'])
 def test_public_revoke_removed(self):self.check(self.sql.replace('REVOKE ALL ON synqoria_triage_secure_cases_quarantine FROM PUBLIC;',''), 'missing public revocation')
 def test_force_rls_removed(self):self.check(self.sql.replace('FORCE ROW LEVEL SECURITY;','',1),'FORCE ROW LEVEL SECURITY')
 def test_row_level_security_removed(self):self.check(self.sql.replace('ENABLE ROW LEVEL SECURITY;','',1),'ENABLE ROW LEVEL SECURITY')
 def test_new_policy_not_allowed(self):self.check(self.sql+'\nCREATE POLICY allow_any ON some_table USING (true);','premature')
 def test_grants_not_allowed(self):self.check(self.sql+'\nGRANT SELECT ON anything TO triage_app;','premature')
 def test_bypass_not_allowed(self):self.check(self.sql+'\nALTER ROLE triage_app BYPASSRLS;','premature')
 def test_snapshot_must_be_encrypted(self):self.check(self.sql.replace('encrypted_snapshot bytea NOT NULL','plaintext_snapshot text NOT NULL'),'encrypted')
 def test_scope_pk_required(self):self.check(self.sql.replace('PRIMARY KEY (tenant_id,facility_id,patient_id,case_id)','PRIMARY KEY (case_id)',1),'scoped case key')
 def test_outbox_integrity_link(self):self.check(self.sql.replace('FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id)','FOREIGN KEY (command_id)'), 'scoped event/outbox linkage')
 def test_no_plaintext_clinical_text(self):self.check(self.sql+'\nALTER TABLE t ADD COLUMN metadata jsonb;','plaintext clinical')
 def test_unpublished_index_required(self):self.check(self.sql.replace('CREATE INDEX triage_secure_unpublished_outbox_quarantine','CREATE INDEX missing_index'),'unpublished outbox index')
if __name__=='__main__':unittest.main()
