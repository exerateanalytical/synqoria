"""Source-level quarantine and outbox-delivery protocol gates; NOT PostgreSQL execution."""
import pathlib, re, unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
MIG=(ROOT/'services/clinical-core/db/migration/V021__longitudinal_outbox_delivery_quarantine.sql').read_text()
JDBC=(ROOT/'services/clinical-core/src/main/java/org/synqoria/longitudinal/LongitudinalOutboxJdbc.java').read_text()
WORKER=(ROOT/'services/clinical-core/src/main/java/org/synqoria/longitudinal/LongitudinalOutboxDelivery.java').read_text()

class OutboxQuarantineTests(unittest.TestCase):
 def test_table_scoped_and_keyed(self):
  for field in ('tenant_id','facility_id','patient_id','encounter_id','command_id','revision','event_hash','event_type','lease_token','lease_expires_at','published_at','parked_at'):
   self.assertIn(field,MIG)
  self.assertIn('FOREIGN KEY (tenant_id,facility_id,patient_id,encounter_id,command_id)',MIG)
 def test_forced_rls_without_runtime_grants(self):
  self.assertIn('ENABLE ROW LEVEL SECURITY',MIG)
  self.assertIn('FORCE ROW LEVEL SECURITY',MIG)
  self.assertIn('REVOKE ALL ON synqoria_longitudinal_delivery_quarantine FROM PUBLIC',MIG)
  for pattern in (r'(?im)^\s*CREATE\s+POLICY',r'(?im)^\s*GRANT\s',r'(?im)^\s*ALTER\s+ROLE\s.*BYPASSRLS',r'(?im)^\s*SET\s+row_security\s*=\s*off'):
   self.assertNotRegex(MIG,pattern)
 def test_database_serial_order(self):
  self.assertIn('SELECT min(x.revision)',JDBC)
  self.assertIn('SKIP LOCKED',JDBC)
  self.assertIn('published_at IS NULL',JDBC)
  self.assertIn('parked_at IS NULL',JDBC)
  self.assertIn('NOT EXISTS',JDBC) # drains projected rows and can seed later revisions
 def test_fencing_and_lease(self):
  self.assertIn('lease_token=?',JDBC)
  self.assertIn('lease_expires_at>clock_timestamp()',JDBC)
  self.assertIn('clock_timestamp()',JDBC)
  self.assertIn('attempts>=12',JDBC)
 def test_scope_installed_for_every_transaction(self):
  self.assertIn('LongitudinalJdbcScope.install(db,scope)',JDBC)
  self.assertIn('SET LOCAL lock_timeout',JDBC)
  self.assertIn('db.rollback()',JDBC)
 def test_private_metadata_only(self):
  self.assertIn('record EventRef(',WORKER)
  self.assertNotRegex(WORKER,r'(?i)(record\s+EventRef[^\n]*\b(body|noteBody|observationValue|patientName)\b)')
  self.assertIn('consumer',WORKER)
 def test_no_auto_publication_or_sql_elevation(self):
  for text in (MIG,JDBC):
   self.assertNotRegex(text,r'(?im)^\s*(NOTIFY\s|GRANT\s|CREATE\s+POLICY|SET\s+ROLE)')
 def test_no_migration_reuse(self):
  versions=list((ROOT/'services/clinical-core/db/migration').glob('V021__*.sql'))
  self.assertEqual(len(versions),1)

if __name__=='__main__': unittest.main()
