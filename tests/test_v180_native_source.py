"""Static integration check only; never claims Expo runtime or device execution."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
staff=(r/'apps/staff-mobile/RegistrationPreview.tsx').read_text()
assert 'lockedRegistrationJourney(locale).map(' in staff
assert 'step.label' in staff and 'accessibilityRole="text"' in staff
for app in ('staff-mobile','patient-mobile'):
 src=(r/'apps'/app/'App.tsx').read_text()
 assert 'lockedNavigationAccessibility(' in src
 assert 'lockedTabA11y' in src and 'disabled={!nativeMoreCanOpen(tab.id,false)}' in src
 assert "privacy.hide();visibility.hide()" in src and "privacy.foreground()" in src
 assert 'verified API authentication is not connected' in src
print('PASS: native read-only models wired in staff/patient source; Expo/device NOT built')
