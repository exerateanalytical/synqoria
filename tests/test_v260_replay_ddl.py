import unittest,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
class Replay(unittest.TestCase):
 def test_unique_command_per_tenant(self):
  s=(ROOT/'services/clinical-core/db/migration/V011__review_command_replay_quarantine.sql').read_text().lower()
  self.assertIn('unapplied',s);self.assertIn('create unique index',s)
  self.assertIn('(tenant_id,facility_id,command_id)',s)
  self.assertIn('revoke all',s);self.assertNotIn('grant insert',s)
  self.assertNotIn('drop policy',s);self.assertNotIn('disable row level security',s)
  self.assertIn('review existing duplicate commands',s)
if __name__=='__main__':unittest.main()
