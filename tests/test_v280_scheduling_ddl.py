"""Additional P06-B0003 source contract check, not PostgreSQL execution."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'services/clinical-core/db/migration/V012__appointment_reservation_quarantine.sql').read_text()
for token in ('CREATE EXTENSION IF NOT EXISTS btree_gist','EXCLUDE USING gist',
              'tenant_id WITH =','facility_id WITH =','resource_id WITH =',
              "tstzrange(starts_at,ends_at,'[)') WITH &&",
              "WHERE (lifecycle = 'CONFIRMED')",'FORCE ROW LEVEL SECURITY',
              'REVOKE ALL','appointment_positive_window'):
    assert token in s,token
assert 'GRANT ' not in s and 'CREATE POLICY ' not in s
print('PASS: P06 quarantine DDL static constraint/RLS/no-grant audit; NOT PostgreSQL tested')
