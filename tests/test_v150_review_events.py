from pathlib import Path
from collections import Counter
p=Path(__file__).resolve().parents[1]
s=(p/'services/clinical-core/db/migration/V008__registration_review_event_quarantine.sql').read_text()
for phrase in ['PRIMARY KEY(tenant_id,facility_id,event_id)','UNIQUE(tenant_id,facility_id,review_id,event_version)',
               'FOREIGN KEY(tenant_id,facility_id,review_id)','ENABLE ROW LEVEL SECURITY',
               'FORCE ROW LEVEL SECURITY','REVOKE ALL ON patient_registration_review_events FROM PUBLIC']:
 assert phrase in s,phrase
assert 'CREATE POLICY' not in s.upper() and 'GRANT SELECT' not in s.upper() and 'GRANT INSERT' not in s.upper()
assert 'REVIEW_REVOKED' in s and 'audit_binding_sha256' in s
print('PASS P01-B0031 draft SQL constraints and fail-closed RLS: STATIC ONLY, PostgreSQL not run')
