"""P01-B0005 static assertions; NOT a PostgreSQL migration test."""
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'services/clinical-core/db/migration/V003__patient_identity_consent_foundation.sql').read_text()
checks=['CREATE TABLE patient_master_index (','CREATE TABLE patient_identifier_tokens (','CREATE TABLE patient_consent_records (',
 'FOREIGN KEY (tenant_id, home_facility_id) REFERENCES hospital_facilities(tenant_id, facility_id)',
 'FOREIGN KEY (tenant_id, patient_id) REFERENCES patient_master_index(tenant_id, patient_id)',
 'identifier_token bytea NOT NULL CHECK (octet_length(identifier_token) = 32)',
 'CHECK ((identity_status = \'MERGED\') = (merged_into_patient_id IS NOT NULL))',
 'CHECK ((status = \'REVOKED\') = (revoked_at IS NOT NULL))',
 'ALTER TABLE patient_master_index FORCE ROW LEVEL SECURITY;',
 'ALTER TABLE patient_identifier_tokens FORCE ROW LEVEL SECURITY;',
 'ALTER TABLE patient_consent_records FORCE ROW LEVEL SECURITY;',
 'REVOKE ALL ON patient_master_index, patient_identifier_tokens, patient_consent_records FROM PUBLIC;']
for fragment in checks:assert fragment in s,fragment
assert 'CREATE POLICY' not in s
print(f'PASS: {len(checks)+1} patient/consent SQL static assertions; PostgreSQL NOT executed')
