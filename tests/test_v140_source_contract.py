"""Static source and OpenAPI check only. No live PostgreSQL, Next.js or Expo integration certification."""
from pathlib import Path
import yaml,json
p=Path(__file__).resolve().parents[1]
s=yaml.safe_load((p/'contracts/openapi/synqoria-registration-release-gate.yaml').read_text())
assert s['openapi']=='3.1.0' and s['paths']=={}
a=s['components']['schemas']['RegistrationReleaseGate']
assert a['additionalProperties'] is False
assert set(a['properties'])==set(a['required'])
assert all(value['const'] is False for value in a['properties'].values())
assert 'REGISTRATION_OUTCOME_UNKNOWN' in s['components']['schemas']['RegistrationUnavailable']['properties']['code']['enum']
sql=(p/'services/clinical-core/db/migration/V007__registration_review_quarantine.sql').read_text()
assert 'CREATE TABLE patient_registration_review_decisions' in sql
for required in ['candidate_revision','command_id uuid NOT NULL','patient_id uuid NOT NULL','reviewer_id <> registrar_id','fingerprint_hmac','revoked_at','FORCE ROW LEVEL SECURITY','REVOKE ALL ON patient_registration_review_decisions FROM PUBLIC']:
 assert required in sql,required
assert 'CREATE POLICY' not in sql and 'GRANT SELECT' not in sql
web=(p/'apps/web/app/workforce/patients/new/page.tsx').read_text()
assert 'registrationFieldStructure' in web and 'registrationIssueSummary' in web
assert 'aria-live="polite"' in web and 'disabled aria-describedby="intake-unavailable"' in web
assert 'fetch(' not in web and 'localStorage' not in web and '<input' not in web
mobile=(p/'apps/staff-mobile/App.tsx').read_text()
assert "import {RegistrationPreview} from './RegistrationPreview'" in mobile
assert '<RegistrationPreview locale={locale}/>' in mobile
assert 'disabled={true}' in mobile
screen=json.loads((p/'registers/canonical-screens.json').read_text())['screen_register']
assert len(screen)==261 and next(x for x in screen if x['id']=='M01-S02')['code_status']=='not_implemented'
print('PASS P01-B0030/P02-B0033-36: unexposed contract, RLS quarantine, read-only UI sources, intact canonical inventory')
