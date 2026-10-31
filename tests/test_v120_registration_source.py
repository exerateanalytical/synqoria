"""Static checks: v0.12.0 draft SQL and locked intake source. Not runtime web/Postgres certification."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
s=(r/'services/clinical-core/db/migration/V005__patient_registration_foundation.sql').read_text()
tables=['patient_registration_demographics','patient_registration_audit','patient_registration_command_claim']
for t in tables:
 assert f'CREATE TABLE {t}' in s,t
 assert f'ALTER TABLE {t} ENABLE ROW LEVEL SECURITY;' in s,t
 assert f'ALTER TABLE {t} FORCE ROW LEVEL SECURITY;' in s,t
assert 'CREATE POLICY' not in s
assert "birth_information_status IN ('KNOWN','ESTIMATED_AGE','UNKNOWN')" in s
assert s.count('FOREIGN KEY (tenant_id, patient_id, facility_id) REFERENCES patient_master_index')==3
assert 'fingerprint_hmac bytea NOT NULL CHECK (octet_length(fingerprint_hmac) = 32)' in s
assert 'REVOKE ALL ON' in s and 'FROM PUBLIC;' in s
web=(r/'apps/web/app/workforce/patients/new/page.tsx').read_text()
assert 'M01-S02' in web and 'useState' in web and 'validateRegistrationDraft' in web
assert 'disabled aria-describedby="intake-unavailable"' in web
assert 'setLocale' in web and "'fr'" in web
assert 'localStorage' not in web and 'fetch(' not in web and 'POST' not in web
style=(r/'apps/web/app/globals.css').read_text()
assert '.intake-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))' in style
assert '@media(max-width:600px){.intake-grid{grid-template-columns:minmax(0,1fr)}' in style
assert '.intake-grid input:not([type="radio"]){min-height:48px' in style
# Inventory IDs and status are preserved; no clinical acceptance claim.
screens=json.loads((r/'registers/canonical-screens.json').read_text())['screen_register']
assert len(screens)==261 and next(z for z in screens if z['id']=='M01-S02')['code_status']=='not_implemented'
print('PASS v0.12.0 registration SQL + locked responsive screen source (static only, no compiled Next/PG)')
