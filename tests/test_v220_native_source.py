from pathlib import Path
r=Path(__file__).resolve().parents[1]
for app in ('staff-mobile','patient-mobile'):
 s=(r/'apps'/app/'App.tsx').read_text()
 for token in ('NativePrivacyCurtain','nativePrivacyAnnouncement','nativeAccessStatus','curtain.background()','curtain.resume()','setPrivacyCovered(true)','accessibilityLiveRegion','disabled={true}'):
  assert token in s,(app,token)
 assert 'curtain.confirm(' not in s,app # no untrusted client unlock
 assert "#F7F5F0" in s and "#073656" in s
 if app=='staff-mobile':assert 'nativeStaffScope' in s
 else:assert 'nativeProxyGrantStatus' in s
for app in ('staff-mobile','patient-mobile','web'):
 import json
 p=json.loads((r/'apps'/app/'package.json').read_text())
 assert p['dependencies']['@synqoria/ux-core']==json.loads((r/'packages/ux-core/package.json').read_text())['version']
print('PASS v0.22.0 native source: both app integrations and workspace version alignment; NOT compiled or device-tested')
