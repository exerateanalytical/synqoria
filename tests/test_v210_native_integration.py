from pathlib import Path
root=Path(__file__).resolve().parents[1]
for app in ['staff-mobile','patient-mobile']:
 src=(root/'apps'/app/'App.tsx').read_text()
 for token in ['nativeLockedAnnouncement','nativeOfflineAction','accessibilityLiveRegion','allowFontScaling','clinicalAction.reason']:
  assert token in src,(app,token)
 assert 'disabled={!nativeMoreCanOpen(tab.id,false)}' in src,app
print('PASS P02 native source integration: 2 separate apps, locked and accessible; not compiled/device-tested')
