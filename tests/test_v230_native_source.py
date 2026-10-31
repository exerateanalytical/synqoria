from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'apps/staff-mobile/App.tsx').read_text()
p=(r/'apps/patient-mobile/App.tsx').read_text()
for name,text in [('staff',s),('patient',p)]:
 for fragment in ['NativeResponseEpoch','NativeOfflineWriteQueue','nativeDeepLinkGate','responseEpoch.invalidate()','queueStatus.reason','disabled={!nativeMoreCanOpen(tab.id,false)}','SafeAreaView','KeyboardAvoidingView']:
  assert fragment in text,(name,fragment)
 assert 'registration writes enabled' not in text
assert 'NativeScopeTransition' in s and 'scopeTransition.suspend()' in s
assert 'NativePrivacyCurtain' in p and 'nativeProxyGrantStatus' in p
assert "nativeDeepLinkGate('/work','staff',false)" in s
assert "nativeDeepLinkGate('/home','patient',false)" in p
print('PASS native source privacy, stale-response and locked navigation wiring; no native binary tested')
