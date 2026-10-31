from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
sql=(root/'services/clinical-core/db/migration/V004__clinical_read_audit_foundation.sql').read_text()
java=(root/'services/clinical-core/src/main/java/org/synqoria/persistence/AuditWriteJdbc.java').read_text()
assert 'CREATE TABLE clinical_read_audit' in sql
for name in ['tenant_id','facility_id','event_id','actor_id','patient_id','operation','occurred_at','correlation_id']:
 assert re.search(r'\b'+name+r'\b',sql), name
 assert name in java,name
assert 'PRIMARY KEY (tenant_id, facility_id, event_id)' in sql
assert 'FOREIGN KEY (tenant_id, patient_id)' in sql
assert 'ENABLE ROW LEVEL SECURITY' in sql and 'FORCE ROW LEVEL SECURITY' in sql
assert 'REVOKE ALL ON clinical_read_audit FROM PUBLIC' in sql
assert not re.search(r'\bCREATE\s+POLICY\b',sql,re.I)
assert '?' in java and 'executeUpdate()' in java
print('PASS v100 clinical audit schema/statement alignment; STATIC only, PostgreSQL not executed')
