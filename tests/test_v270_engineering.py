"""P01-B0039–B0042: real source and synthetic-fixture tests, not application builds."""
import unittest,sys,tempfile,pathlib,json,shutil,zipfile,hashlib
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
from dependency_compatibility import audit as pins
from verify_lock_integrity import audit as lock
from run_app_builds import execute as builds
from verify_cumulative_archive import audit as archive,MANIFEST
class Engineering(unittest.TestCase):
 def test_pins(self):
  self.assertEqual(pins(ROOT),[])
  with tempfile.TemporaryDirectory() as tmp:
   r=pathlib.Path(tmp);shutil.copy(ROOT/'package.json',r/'package.json')
   for w in ('apps/web','apps/staff-mobile','apps/patient-mobile','packages/ux-core'):
    (r/w).mkdir(parents=True);shutil.copy(ROOT/w/'package.json',r/w/'package.json')
   m=json.loads((r/'apps/web/package.json').read_text());m['dependencies']['next']='^15.5.6';(r/'apps/web/package.json').write_text(json.dumps(m))
   self.assertTrue(any('floating_pin' in x for x in pins(r)))
 def test_lock_integrity(self):
  self.assertIn('missing_or_invalid_lockfile',lock(ROOT))
  with tempfile.TemporaryDirectory() as tmp:
   r=pathlib.Path(tmp);shutil.copy(ROOT/'package.json',r/'package.json');packages={'':{'version':json.loads((ROOT/'package.json').read_text())['version']}}
   for w in ('apps/web','apps/staff-mobile','apps/patient-mobile','packages/ux-core'):
    (r/w).mkdir(parents=True);shutil.copy(ROOT/w/'package.json',r/w/'package.json');packages[w]=json.loads((r/w/'package.json').read_text())
   packages['node_modules/synthetic']={'version':'1.0.0','integrity':'sha512-QUJDRA==','resolved':'https://registry.npmjs.org/synthetic/-/synthetic-1.0.0.tgz'}
   (r/'package-lock.json').write_text(json.dumps({'lockfileVersion':3,'packages':packages}))
   self.assertEqual(lock(r),[])
   packages['node_modules/synthetic']['resolved']='https://evil.example/synthetic.tgz';(r/'package-lock.json').write_text(json.dumps({'lockfileVersion':3,'packages':packages}))
   self.assertTrue(any('untrusted_resolution' in x for x in lock(r)))
 def test_build_attempt_fail_closed(self):
  result=builds(ROOT)
  self.assertEqual(result['status'],'BLOCKED');self.assertEqual(result['builds_passed'],0);self.assertEqual(result['checks'],[])
  self.assertTrue(any('lock' in x for x in result['blockers']))
  # Command runner itself tested separately; no mock application build is reported.
 def test_archive_integrity(self):
  with tempfile.TemporaryDirectory() as tmp:
   p=pathlib.Path(tmp)/'test.zip';body=b'synthetic content';m={'version':'test','files':{'a.txt':hashlib.sha256(body).hexdigest()}}
   with zipfile.ZipFile(p,'w') as z:z.writestr('a.txt',body);z.writestr(MANIFEST,json.dumps(m))
   self.assertEqual(archive(p),[])
   with zipfile.ZipFile(p,'w') as z:z.writestr('a.txt',b'tampered');z.writestr(MANIFEST,json.dumps(m))
   self.assertTrue(any('digest_mismatch' in x for x in archive(p)))
   with zipfile.ZipFile(p,'w') as z:z.writestr('../escape',body);z.writestr(MANIFEST,json.dumps(m))
   self.assertTrue(any('unsafe_path' in x for x in archive(p)))
if __name__=='__main__':unittest.main()
