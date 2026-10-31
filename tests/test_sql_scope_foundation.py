"""Offline static assertions only; does not claim PostgreSQL execution."""
from pathlib import Path
import re
s=(Path(__file__).resolve().parents[1]/'services/clinical-core/db/migration/V001__hospital_scope_foundation.sql').read_text()
tables=['hospital_cells','hospital_tenants','hospital_facilities','hospital_departments','hospital_locations']
for table in tables:
    assert f'CREATE TABLE {table} (' in s,table
    assert f'ALTER TABLE {table} ENABLE ROW LEVEL SECURITY;' in s,table
    assert f'ALTER TABLE {table} FORCE ROW LEVEL SECURITY;' in s,table
assert 'FOREIGN KEY (tenant_id, cell_id) REFERENCES hospital_tenants(tenant_id, cell_id)' in s
assert 'FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id)' in s
assert re.search(r'FOREIGN KEY \(tenant_id, facility_id, department_id\)\s+REFERENCES hospital_departments\(tenant_id, facility_id, department_id\)',s)
assert s.count('CREATE TABLE ')==5
assert s.count('CREATE POLICY ') == 0
assert 'FROM PUBLIC;' in s
for table in tables[1:]:
    body=s.split(f'CREATE TABLE {table} (',1)[1].split(');',1)[0]
    assert 'revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0)' in body
assert 'session variables alone are NOT a trust boundary' in s
print('PASS: 5 domain scope DDL definitions, composite FKs, default-deny RLS source (SQL NOT EXECUTED)')
