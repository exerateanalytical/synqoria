import importlib.util, tempfile,unittest,shutil,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
import sys
sys.path.insert(0,str(ROOT/'tools'))
from migration_checksum_gate import verify
class ChecksumGateTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
  self.folder=Path(self.temp.name)/'migration';shutil.copytree(ROOT/'services/clinical-core/db/migration',self.folder)
  self.lock=Path(self.temp.name)/'lock.json';shutil.copy(ROOT/'registers/migration-sha256-lock.json',self.lock)
 def test_real_lock(self):self.assertEqual(verify(self.folder,self.lock)['locked'],len(json.loads(self.lock.read_text())['files']))
 def test_modified_migration_fails(self):
  p=next(self.folder.glob('V001__*.sql'));p.write_bytes(p.read_bytes()+b'\n-- tampered')
  with self.assertRaisesRegex(ValueError,'modified'):verify(self.folder,self.lock)
 def test_deleted_migration_fails(self):
  next(self.folder.glob('V014__*.sql')).unlink()
  with self.assertRaises(ValueError):verify(self.folder,self.lock)
 def test_unlocked_migration_fails(self):
  (self.folder/'V018__unreviewed.sql').write_text('SELECT 1;')
  with self.assertRaisesRegex(ValueError,'inventory mismatch'):verify(self.folder,self.lock)
 def test_bad_digest_fails(self):
  data=json.loads(self.lock.read_text());key=next(iter(data['files']));data['files'][key]='not-a-digest';self.lock.write_text(json.dumps(data))
  with self.assertRaisesRegex(ValueError,'invalid checksum digest'):verify(self.folder,self.lock)
 def test_missing_lock_fails(self):
  self.lock.unlink()
  with self.assertRaisesRegex(ValueError,'missing'):verify(self.folder,self.lock)
if __name__=='__main__':unittest.main()
