"""P02-B0089/P02-B0091 actual ShellFrame source contract checks, not a Next build."""
import unittest,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
class Shell(unittest.TestCase):
 def test_more_focus(self):
  s=(ROOT/'apps/web/components/ShellFrame.tsx').read_text()
  controller=(ROOT/'apps/web/lib/mobile-more-controller.ts').read_text()
  for token in ('attachMobileMoreDisclosure(moreButton.current,morePanel.current',
                'moreButton.current?.focus()', 'aria-controls="mobile-more-panel"',
                'aria-expanded={moreOpen}', 'hidden={!moreOpen}', 'aria-haspopup="true"'):
   self.assertIn(token,s)
  for token in ("event.key === 'Escape'", "event.key !== 'Tab'",
                "document.addEventListener('pointerdown', pointer)",
                "document.removeEventListener('pointerdown', pointer)",
                "window.addEventListener('resize', resize)",
                "window.removeEventListener('resize', resize)"):
   self.assertIn(token,controller)
  self.assertIn('grantedCapabilities:new Set()',s);self.assertIn('licensedModules:new Set()',s)
 def test_locale_propagation(self):
  s=(ROOT/'apps/web/components/ShellFrame.tsx').read_text()
  for token in ('new URLSearchParams(window.location.search)','requested===\'fr\'','document.documentElement.lang=locale','const localized=(path:string)', 'href={localized("/workforce")}', 'href={localized("/portal")}', 'href={localized("/platform")}', 'href={localized("/")}'):
   self.assertIn(token,s)
  self.assertNotIn('localStorage',s);self.assertNotIn('sessionStorage',s)
if __name__=='__main__':unittest.main()
