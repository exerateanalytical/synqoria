import unittest, pathlib, json, tempfile, shutil, sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from offline_build_evidence import execute
from dependency_release_gate import audit
class Foundation(unittest.TestCase):
 def test_runner(self):
  result=execute([('true',[sys.executable,'-c','print(7)']),('fail',[sys.executable,'-c','import sys;sys.exit(9)'])],ROOT)
  self.assertFalse(result['passed']);self.assertEqual([r['exit_code'] for r in result['checks']],[0,9]);self.assertIn('7',result['checks'][0]['stdout'])
  self.assertTrue(execute([('pass',[sys.executable,'-c','pass'])],ROOT)['passed'])
  self.assertEqual(execute([('missing',['/no/such/command'])],ROOT)['checks'][0]['exit_code'],127)
 def test_lock(self):
  self.assertIn('missing_package_lock',audit(ROOT))
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)
   for rel in ['package.json','apps/web/package.json','apps/staff-mobile/package.json','apps/patient-mobile/package.json','packages/ux-core/package.json']:
    f=p/rel;f.parent.mkdir(parents=True,exist_ok=True);shutil.copy(ROOT/rel,f)
   self.assertIn('missing_package_lock',audit(p))
   root=json.loads((p/'package.json').read_text())
   lock={'lockfileVersion':3,'packages':{'':{'version':root['version']},**{w:{} for w in ('apps/web','apps/staff-mobile','apps/patient-mobile','packages/ux-core')}}}
   (p/'package-lock.json').write_text(json.dumps(lock))
   # v0.27.0 pinned manifests now pass this gate once a synthetic lock is present.
   self.assertEqual(audit(p),[])
   for w in ('apps/web','apps/staff-mobile','apps/patient-mobile','packages/ux-core'):
    f=p/w/'package.json';m=json.loads(f.read_text())
    for section in ('dependencies','devDependencies'):
     for name,value in m.get(section,{}).items():
      if value.startswith(('~','^')):m[section][name]=value[1:]
    f.write_text(json.dumps(m))
   self.assertEqual(audit(p),[])
   m=json.loads((p/'apps/web/package.json').read_text());m['dependencies']['next']='^15.5.6';(p/'apps/web/package.json').write_text(json.dumps(m))
   self.assertTrue(any('unfixed_external_dependency:apps/web:next'==x for x in audit(p)))
 def test_ci(self):
  s=(ROOT/'.github/workflows/engineering-checks.yml').read_text()
  for x in ['application-builds-not-clinical-release','tools/dependency_release_gate.py','npm ci --ignore-scripts','tools/run_app_builds.py']:
   self.assertIn(x,s)
  runner=(ROOT/'tools/run_app_builds.py').read_text()
  self.assertIn("'npm','run','build','--workspace','@synqoria/web'",runner)
  self.assertEqual(runner.count("'expo','export','--platform','all'"),2)
  self.assertIn('apps/staff-mobile',runner)
  self.assertIn('apps/patient-mobile',runner)
  self.assertNotIn('continue-on-error: true',s)
  self.assertIn('NOT clinical release',s)
if __name__=='__main__':unittest.main()
