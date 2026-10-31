"""Validate mock-tested JDBC selectors track canonical DDL identifiers; NOT SQL execution."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=(r/'services/clinical-core/src/main/java/org/synqoria/persistence/TenantScopedPatientJdbc.java').read_text()
f=(r/'services/clinical-core/src/main/java/org/synqoria/persistence/TenantScopedFacilityJdbc.java').read_text()
a=(r/'services/clinical-core/db/migration/V003__patient_identity_consent_foundation.sql').read_text()
b=(r/'services/clinical-core/db/migration/V001__hospital_scope_foundation.sql').read_text()
assert 'FROM patient_master_index' in p and 'home_facility_id' in p and 'lower(identity_status)' in p
assert 'CREATE TABLE patient_master_index' in a and 'home_facility_id uuid' in a
assert 'FROM hospital_facilities' in f and 'facility_status AS status' in f
assert 'CREATE TABLE hospital_facilities' in b and 'facility_status text' in b
assert 'patient_identity WHERE' not in p and 'FROM facility WHERE' not in f
print('PASS: JDBC SQL selectors aligned with exact source DDL tables and column names (static only)')
