from pathlib import Path
import re
p=Path(__file__).resolve().parents[1]/'services/clinical-core/db/migration/V022__longitudinal_inbox_consumer_quarantine.sql'
s=p.read_text()
for table in ['synqoria_longitudinal_inbox_quarantine','synqoria_longitudinal_inbox_cursor_quarantine']:
    assert f'CREATE TABLE {table}' in s
    assert f'ALTER TABLE {table} ENABLE ROW LEVEL SECURITY;' in s
    assert f'ALTER TABLE {table} FORCE ROW LEVEL SECURITY;' in s
    assert f'REVOKE ALL ON {table} FROM PUBLIC;' in s
for term in ['tenant_id uuid NOT NULL','facility_id uuid NOT NULL','patient_id uuid NOT NULL',
             'encounter_id uuid NOT NULL','consumer_id uuid NOT NULL','event_id uuid NOT NULL',
             'UNIQUE(tenant_id,facility_id,patient_id,encounter_id,consumer_id,replay_key)',
             "state='APPLIED' AND applied_at IS NOT NULL", "state='CLAIMED' AND lease_token IS NOT NULL"]:
    assert term in s,term
assert not re.search(r'^\s*CREATE\s+POLICY\b|^\s*GRANT\b',s,re.M|re.I)
assert 'patient_name' not in s and 'note_body' not in s
print('PASS P07-B0115 quarantined inbox SQL: 19 scope/security checks; NOT live PostgreSQL')
