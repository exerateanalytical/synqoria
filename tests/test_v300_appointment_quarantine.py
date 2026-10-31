from pathlib import Path
p=Path(__file__).resolve().parents[1]/'services/clinical-core/db/migration/V013__appointment_restricted_audit_quarantine.sql'
s=p.read_text().lower()
assert s.count('create table if not exists')==2
assert s.count('enable row level security')==2
assert s.count('force row level security')==2
assert s.count('revoke all on')==2
assert 'create policy ' not in s
assert 'grant insert' not in s and 'grant update' not in s
assert 'unique (tenant_id,facility_id,appointment_id,appointment_revision)' in s
assert 'event_type text not null' in s
print('PASS 8 static quarantine migration assertions; no live database executed')
