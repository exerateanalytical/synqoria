"""P01-B0004/B0005 static source invariants ONLY. PostgreSQL not executed."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]/'services/clinical-core/db/migration'
files={'V002__outbox_inbox_foundation.sql':('clinical_outbox','clinical_inbox'),
       'V003__patient_identity_consent_foundation.sql':('patient_master_index','patient_identifier_tokens','patient_consent_records')}
checks=0
for name,tables in files.items():
 s=(root/name).read_text()
 for t in tables:
  for fragment in (f'CREATE TABLE {t} (',f'ALTER TABLE {t} ENABLE ROW LEVEL SECURITY;',f'ALTER TABLE {t} FORCE ROW LEVEL SECURITY;'):
   assert fragment in s,(name,t,fragment);checks+=1
 assert 'CREATE POLICY' not in s and 'FROM PUBLIC;' in s;checks+=1
 assert 'FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id)' in s or name.startswith('V003');checks+=1
out=(root/'V002__outbox_inbox_foundation.sql').read_text()
assert 'UNIQUE (tenant_id, facility_id, aggregate_type, aggregate_id, aggregate_revision, event_type)' in out;checks+=1
assert 'PRIMARY KEY (tenant_id, facility_id, consumer_id, event_id)' in out;checks+=1
assert 'WHERE delivered_at IS NULL' in out;checks+=1
ident=(root/'V003__patient_identity_consent_foundation.sql').read_text()
for s in ['identifier_token bytea NOT NULL CHECK (octet_length(identifier_token) = 32)',
          'FOREIGN KEY (tenant_id, merged_into_patient_id) REFERENCES patient_master_index(tenant_id, patient_id)',
          'CHECK ((identity_status = \'MERGED\') = (merged_into_patient_id IS NOT NULL))',
          "CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))"]:
 assert s in ident;checks+=1
assert len(re.findall(r'CREATE TABLE ',out))==2;checks+=1
assert len(re.findall(r'CREATE TABLE ',ident))==3;checks+=1
print(f'PASS: {checks} static SQL assertions across V002/V003; no DB execution')
