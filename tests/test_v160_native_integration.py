from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'apps/staff-mobile/RegistrationPreview.tsx').read_text()
assert 'intakeA11ySummary' in s and "['ACCESS','FACILITY','REVIEW','OFFLINE']" in s
assert 'accessibilityRole="alert"' in s and 'nativeID="intake-status"' in s
assert 'summary.items.map' in s and 'registrationActionPresentation(locale,false,false)' in s
assert 'disabledAction' in s and 'TextInput' not in s and 'onChangeText' not in s
assert 'flexShrink:1' in s
print('PASS P02-B0043 native source integration; source-only, not Expo build')
