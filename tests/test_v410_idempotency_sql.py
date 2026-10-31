from pathlib import Path
p=Path(__file__).resolve().parents[1]/'services/clinical-core/db/migration/V014__appointment_idempotency_quarantine.sql'
s=p.read_text()
required=['CREATE TABLE IF NOT EXISTS synqoria_appointment_idempotency_quarantine',
 'tenant_id uuid NOT NULL','facility_id uuid NOT NULL','actor_id uuid NOT NULL',
 'patient_id uuid NOT NULL','key_digest text NOT NULL','command_digest text NOT NULL',
 'UNIQUE (tenant_id,facility_id,actor_id,key_digest)',
 "state IN ('PENDING','COMMITTED')",'appointment_id uuid NOT NULL',
 'ENABLE ROW LEVEL SECURITY','FORCE ROW LEVEL SECURITY',
 'REVOKE ALL ON synqoria_appointment_idempotency_quarantine FROM PUBLIC']
for token in required: assert token in s,token
assert 'CREATE POLICY ' not in s
assert 'GRANT ' not in s
assert 'BYPASSRLS' in s
assert 'result_revision IS NULL AND event_id IS NULL' in s
assert 'result_revision IS NOT NULL AND event_id IS NOT NULL' in s
print(f'PASS idempotency quarantine migration static checks={len(required)+5}; PostgreSQL not executed')
