"""Static source integration only. No Expo dependency resolution, device or native build."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
for app in ('staff-mobile','patient-mobile'):
 s=(r/'apps'/app/'App.tsx').read_text()
 assert 'AppState.addEventListener' in s and "privacy.hide();visibility.hide()" in s and "privacy.foreground()" in s
 assert 'visibility.hide()' in s and 'sub.remove()' in s
 assert 'disabled={true}' in s and 'No verified server session adapter installed' in s
 assert 'mobileShellTabs(' in s
assert 'proxyContext.background()' in (r/'apps/patient-mobile/App.tsx').read_text()
s=(r/'apps/staff-mobile/RegistrationPreview.tsx').read_text()
assert "accessibleLockedStatus(locale,'CLINICAL_RELEASE_LOCKED')" in s
assert 'accessibilityRole="alert"' in s and 'release.message' in s
print('PASS v0.17 native source integration: staff/patient AppState, proxy clear, accessible locked status; NO native build')
