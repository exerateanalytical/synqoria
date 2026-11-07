import unittest,tempfile,json,sys,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from audit_postgres_migrations import audit
from probe_postgres_readiness import assess,probe
from probe_application_builds import audit as build_audit
class Checks(unittest.TestCase):
 def test_migrations(self):
  directory=ROOT/'services/clinical-core/db/migration';r=audit(directory);self.assertTrue(r['passed'],r['issues'])
  with tempfile.TemporaryDirectory() as d:
   dest=Path(d)
   for f in directory.glob('V*.sql'):shutil.copy2(f,dest/f.name)
   f=dest/'V008__registration_review_event_quarantine.sql'
   f.write_text(f.read_text().replace('FORCE ROW LEVEL SECURITY','ENABLE ROW LEVEL SECURITY'))
   self.assertIn('missing_force_rls:patient_registration_review_events',audit(dest)['issues'])
   f.write_text(f.read_text()+'\nGRANT SELECT ON patient_registration_review_events TO PUBLIC;')
   self.assertIn('unsafe_authority_or_destructive_statement',audit(dest)['issues'])
 def test_pg_probe(self):
  self.assertEqual(assess('160001|synqoria_review|on|f|f','synqoria_review'),(True,'restricted_role_metadata_passed_not_table_rls'))
  for raw in ('150000|synqoria_review|on|f|f','160001|postgres|on|t|t','160001|synqoria_review|off|f|f','invalid'):
   self.assertFalse(assess(raw,'synqoria_review')[0])
  class CP: returncode=0;stdout='160001|synqoria_review|on|f|f'
  def run(args,**kw):
   self.assertIn('ON_ERROR_STOP=1',args);self.assertIn('--command',args);self.assertEqual(kw['timeout'],10)
   return CP()
  self.assertTrue(probe('test-dsn','synqoria_review',runner=run,which=lambda x:'/usr/bin/psql')['ready'])
  self.assertFalse(probe('','synqoria_review')['ready'])
 def test_build_probe(self):
  # Main now contains the exact pinned npm lock: an installed checkout can be
  # build-ready. Preserve the historical fail-closed missing-lock case in a fixture.
  r=build_audit(ROOT);self.assertNotIn('missing_root_lockfile',r['issues'])
  with tempfile.TemporaryDirectory() as absent:
   fixture=Path(absent)
   self.assertIn('missing_root_lockfile',build_audit(fixture)['issues'])
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);(p/'package-lock.json').write_text('{}');(p/'package.json').write_text('{"version":"1.0.0"}')
   for app,modules in {'web':('next','react','react-dom'),'staff-mobile':('expo','react-native'),'patient-mobile':('expo','react-native')}.items():
    (p/'apps'/app).mkdir(parents=True);(p/'apps'/app/'package.json').write_text(json.dumps({'version':'1.0.0','dependencies':{m:'1' for m in modules}}))
    for m in modules:
     (p/'node_modules'/m).mkdir(parents=True,exist_ok=True);(p/'node_modules'/m/'package.json').write_text('{}')
   self.assertTrue(build_audit(p)['build_ready'])
   (p/'apps'/'web'/'package.json').write_text('{"version":"0.0.1","dependencies":{}}')
   self.assertFalse(build_audit(p)['build_ready'])
if __name__=='__main__':unittest.main()
