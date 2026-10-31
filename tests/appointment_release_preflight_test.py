import sys,unittest,tempfile,shutil,json,subprocess
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from appointment_release_preflight import preflight,main
ROOT=Path(__file__).resolve().parents[1]
MIGRATIONS=ROOT/'services/clinical-core/db/migration'
LOCK=ROOT/'registers/migration-sha256-lock.json'
class PreflightTests(unittest.TestCase):
    def test_real_migrations_pass_without_runtime_certification(self):
        result=preflight(MIGRATIONS,LOCK)
        self.assertEqual(result['status'],'PASS')
        self.assertFalse(result['database_runtime_verified'])
        self.assertFalse(result['clinical_release_approved'])
        self.assertEqual(result['migration_count'],14)
    def test_missing_lock_denied(self):
        with self.assertRaises(ValueError):preflight(MIGRATIONS,Path('/nonexistent/lock.json'))
    def test_mutated_migration_denied(self):
        with tempfile.TemporaryDirectory() as d:
            dest=Path(d)/'migrations';shutil.copytree(MIGRATIONS,dest)
            target=next(dest.glob('V001__*.sql'))
            target.write_text(target.read_text()+'\n-- unreviewed change\n')
            with self.assertRaises(ValueError):preflight(dest,LOCK)
    def test_unreviewed_new_migration_denied(self):
        with tempfile.TemporaryDirectory() as d:
            dest=Path(d)/'migrations';shutil.copytree(MIGRATIONS,dest)
            (dest/'V015__unsafe.sql').write_text('SELECT 1;')
            with self.assertRaises(ValueError):preflight(dest,LOCK)
    def test_cli_exit_status(self):
        self.assertEqual(main(['--migrations',str(MIGRATIONS),'--lock',str(LOCK)]),0)
        self.assertEqual(main(['--migrations',str(MIGRATIONS),'--lock','/missing']),1)
if __name__=='__main__':unittest.main()
