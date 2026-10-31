"""Source checks only: Expo native build and device accessibility remain unverified."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
for kind in ('staff','patient'):
 app=(root/f'apps/{kind}-mobile/App.tsx').read_text()
 assert 'mobileShellTabs' in app and f"mobileShellTabs('{kind}'" in app
 assert 'safeTabLabels.find(' in app
 assert 'disabled={true}' in app and 'accessibilityState={{selected:false,disabled:true}}' in app
 assert 'setActive(tab.id)' not in app
 assert "{kind:'locked'}" in app
print('PASS: both native shell sources use shared bilingual safe navigation with disabled data tabs; NOT Expo-tested')
