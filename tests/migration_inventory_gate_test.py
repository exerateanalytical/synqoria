import importlib.util, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('inventory',ROOT/'tools/migration_inventory_gate.py')
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
class InventoryTests(unittest.TestCase):
 def test_real_inventory(self):
  report=gate.audit(ROOT/'services/clinical-core/db/migration')
  self.assertGreaterEqual(report['count'],14)
 def test_duplicate_version(self):
  with tempfile.TemporaryDirectory() as tmp:
   d=Path(tmp);(d/'V001__one.sql').write_text('SELECT 1;');(d/'V1__other.sql').write_text('SELECT 2;')
   with self.assertRaisesRegex(ValueError,'duplicate'):gate.audit(d)
 def test_gap(self):
  with tempfile.TemporaryDirectory() as tmp:
   d=Path(tmp);(d/'V001__one.sql').write_text('SELECT 1;');(d/'V003__three.sql').write_text('SELECT 3;')
   with self.assertRaisesRegex(ValueError,'gap'):gate.audit(d)
 def test_empty_and_malformed(self):
  with tempfile.TemporaryDirectory() as tmp:
   d=Path(tmp);(d/'V001__one.sql').write_text('  ')
   with self.assertRaisesRegex(ValueError,'empty'):gate.audit(d)
   (d/'V001__one.sql').write_text('SELECT 1;');(d/'V002_bad.sql').write_text('SELECT 2;')
   with self.assertRaisesRegex(ValueError,'invalid'):gate.audit(d)
if __name__=='__main__':unittest.main()
