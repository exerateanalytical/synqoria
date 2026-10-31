"""Negative mutation tests: each gate must reject a realistic unsafe schema drift."""
import importlib.util
from pathlib import Path
import sys,unittest
p=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('gate',p/'tools/appointment_migration_gate.py')
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
mig=p/'services/clinical-core/db/migration'
r=(mig/'V012__appointment_reservation_quarantine.sql').read_text()
i=(mig/'V014__appointment_idempotency_quarantine.sql').read_text()
a=(mig/'V013__appointment_restricted_audit_quarantine.sql').read_text()
class GateTests(unittest.TestCase):
 def test_b0065_quarantine_and_grants(self):
  for sql in (r,i,a): self.assertTrue(gate.check_quarantine(sql))
  for mutation in (r+'\nGRANT SELECT ON synqoria_appointment_reservations_quarantine TO app;',r+'\nCREATE POLICY permissive ON synqoria_appointment_reservations_quarantine USING (true);',r.replace('_quarantine','_live')):
   with self.assertRaises(AssertionError):gate.check_quarantine(mutation)
 def test_b0066_rls_force_and_public_revoke(self):
  for sql in (r,i,a):self.assertTrue(gate.check_rls(sql))
  for mutation in (r.replace('FORCE ROW LEVEL SECURITY','NO FORCE ROW LEVEL SECURITY'),i.replace('REVOKE ALL ON','-- REVOKE ALL ON'),a.replace('ENABLE ROW LEVEL SECURITY','DISABLE ROW LEVEL SECURITY')):
   with self.assertRaises(AssertionError):gate.check_rls(mutation)
 def test_b0067_scoped_table_columns(self):
  for sql in (r,i,a):self.assertTrue(gate.check_scope(sql))
  for mutation in (r.replace('patient_id uuid NOT NULL','patient_id uuid'),i.replace('facility_id uuid NOT NULL','facility_id uuid'),a.replace('appointment_id uuid NOT NULL','appointment_id uuid')):
   with self.assertRaises(AssertionError):gate.check_scope(mutation)
 def test_b0068_concurrent_reservation_exclusion(self):
  self.assertTrue(gate.check_exclusion(r))
  for mutation in (r.replace('EXCLUDE USING gist','UNIQUE USING gist'),r.replace('resource_id WITH =','resource_id WITH <>'),r.replace("lifecycle = 'CONFIRMED'","lifecycle = 'CANCELLED'"),r.replace("'[)'","'[]'")):
   with self.assertRaises(AssertionError):gate.check_exclusion(mutation)
 def test_b0069_idempotency_replay_integrity(self):
  self.assertTrue(gate.check_idempotency(i))
  for mutation in (i.replace('actor_id,key_digest','patient_id,key_digest'),i.replace("'^[0-9a-f]{64}$'","'^[0-9a-f]+$'"),i.replace('event_id IS NOT NULL','event_id IS NULL')):
   with self.assertRaises(AssertionError):gate.check_idempotency(mutation)
if __name__=='__main__':unittest.main(verbosity=2)
