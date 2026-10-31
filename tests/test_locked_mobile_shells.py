"""P02-B0010 source-level guard; not a device or UI runtime test."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
for app in ('staff-mobile','patient-mobile'):
 s=(root/f'apps/{app}/App.tsx').read_text()
 assert 'disabled={!nativeMoreCanOpen(tab.id,false)}' in s and 'disabled:!nativeMoreCanOpen(tab.id,false)' in s, app
 assert 'setActive(tab.id)' not in s and "setActive('more')" in s, app
 assert 'verified API authentication is not connected' in s, app
 assert s.count("id:'")>=4, app
print('PASS: 2 unauthenticated Expo shells have disabled tab navigation source guards')
