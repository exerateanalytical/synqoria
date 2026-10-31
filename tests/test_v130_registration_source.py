"""Source-only checks, explicitly NOT compiled native or real PostgreSQL validation."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
sql=(r/'services/clinical-core/db/migration/V006__identity_lookup_quarantine.sql').read_text()
assert 'CREATE TABLE patient_identity_lookup' in sql
assert 'lookup_hmac bytea NOT NULL' in sql
assert 'ALTER TABLE patient_identity_lookup ENABLE ROW LEVEL SECURITY;' in sql
assert 'ALTER TABLE patient_identity_lookup FORCE ROW LEVEL SECURITY;' in sql
assert 'REVOKE ALL ON patient_identity_lookup FROM PUBLIC;' in sql
assert 'CREATE POLICY' not in sql
app=(r/'apps/staff-mobile/RegistrationPreview.tsx').read_text()
assert 'RegistrationPreview' in app and 'TextInput' not in app and 'accessibilityRole="header"' in app
assert 'Enregistrement' in app and 'disabled' in app
web=(r/'apps/web/app/workforce/patients/new/page.tsx').read_text()
assert 'registrationIssueSummary' in web and 'aria-live="polite"' in web
assert 'disabled aria-describedby="intake-unavailable"' in web
assert 'fetch(' not in web and 'localStorage' not in web
print('PASS v0.13.0: quarantined identity lookup SQL and locked bilingual web/native intake source')
