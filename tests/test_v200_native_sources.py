"""P02-B0058 / P02-B0061: static source/Expo config verification, NOT device or native builds."""
from pathlib import Path
import json,sys
batch=sys.argv[1] if len(sys.argv)>1 else "all"
assert batch in ("all","P02-B0058","P02-B0061")
r=Path(__file__).resolve().parents[1]
if batch in ('all','P02-B0058'):
 for kind in ('staff','patient'):
  app=(r/f'apps/{kind}-mobile/App.tsx').read_text()
  package=json.loads((r/f'apps/{kind}-mobile/package.json').read_text())
  config=json.loads((r/f'apps/{kind}-mobile/app.json').read_text())
  assert 'react-native-safe-area-context' in package['dependencies']
  assert config['expo']['android']['softwareKeyboardLayoutMode']=='resize'
  for term in ('SafeAreaProvider','SafeAreaView','KeyboardAvoidingView','Platform.OS','nativeTextSafety','MobilePrivacyLatch','mobileScopeDisclosure','privacy.hide();visibility.hide()'):
   assert term in app,(kind,term)
  assert "edges={['top','bottom','left','right']}" in app
  assert 'flexWrap:\'wrap\'' in app
  assert app.count('<KeyboardAvoidingView')==app.count('</KeyboardAvoidingView>')==1
  assert app.count('<SafeAreaProvider')==app.count('</SafeAreaProvider>')==1
 print('PASS P02-B0058 both RN sources keyboard/safe area integration; package/config source only')
if batch in ('all','P02-B0061'):
 preview=(r/'apps/staff-mobile/RegistrationPreview.tsx').read_text()
 assert 'Pressable disabled={true}' in preview
 assert 'accessibilityRole="button"' in preview
 assert 'accessibilityState={{disabled:true}}' in preview
 assert 'registrationReleasePresentation' in preview
 assert '<View style={s.disabledAction}' not in preview
 print('PASS P02-B0061 native disabled action has disabled button semantics; no clinical write')
