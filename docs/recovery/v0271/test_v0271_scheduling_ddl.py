from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
sql=(r/'services/clinical-core/db/migration/V012__appointment_read_quarantine.sql').read_text()
for term in ('CREATE TABLE scheduling_appointments','PRIMARY KEY (tenant_id, facility_id, appointment_id)','FOREIGN KEY (tenant_id, facility_id)','REFERENCES hospital_facilities(tenant_id, facility_id)','REFERENCES patient_master_index(tenant_id, patient_id)','ENABLE ROW LEVEL SECURITY','FORCE ROW LEVEL SECURITY','REVOKE ALL ON scheduling_appointments FROM PUBLIC'):
 assert term in sql,term
assert 'CREATE POLICY' not in sql
j=json.loads((r/'registers/batch-ledger.json').read_text());assert j['version']=='0.27.1'
ids={x['id'] for x in j['batches']}
assert all(f'P06-B{i:04d}' in ids for i in range(10,20))
assert len(ids)==len(j['batches'])
assert len(json.loads((r/'registers/canonical-screens.json').read_text())['screen_register'])==261
assert len(json.loads((r/'registers/canonical-modules.json').read_text())['module_register'])==40
print('PASS v0.27.1 DDL quarantined, composite scope keys, no RLS allow, canonical counts, ten unique recovery batch IDs')
