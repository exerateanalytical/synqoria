import unittest,pathlib,re
ROOT=pathlib.Path(__file__).resolve().parents[1]
class Native(unittest.TestCase):
 def _check(self,role):
  s=(ROOT/f'apps/{role}-mobile/App.tsx').read_text()
  self.assertIn("nativeMoreItems('"+role+"',locale)",s)
  self.assertIn("active==='more'",s)
  self.assertIn('disabled={!nativeMoreCanOpen(tab.id,false)}',s)
  self.assertIn("setActive('more')",s)
  self.assertIn('accessibilityLabel={item.label',s)
  self.assertIn('accessibilityRole="header"',s)
  self.assertIn("setActive(tabs[0].id)",s)
  self.assertIn("setPrivacyCovered(true)",s)
  self.assertIn('maxFontSizeMultiplier',s)
  self.assertEqual(len(re.findall(r"\{id:'(?:work|patients|tasks|home|appointments|records|more)'",s)),4)
  self.assertNotIn('gradient',s.lower());self.assertNotIn('glow',s.lower())
 def test_staff(self):self._check('staff')
 def test_patient(self):self._check('patient')
if __name__=='__main__':unittest.main()
