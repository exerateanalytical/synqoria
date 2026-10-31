"""Source-only contract drift gate for quarantined M02 JDBC SQL; NOT a PostgreSQL execution."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
migrations={
 'reservations':root/'services/clinical-core/db/migration/V012__appointment_reservation_quarantine.sql',
 'audit':root/'services/clinical-core/db/migration/V013__appointment_restricted_audit_quarantine.sql',
 'idempotency':root/'services/clinical-core/db/migration/V014__appointment_idempotency_quarantine.sql',
}
sql='\n'.join(p.read_text() for p in migrations.values())
classes=root/'services/clinical-core/src/main/java/org/synqoria/scheduling'
contracts={
 'AppointmentScopedMutation.java':('synqoria_appointment_reservations_quarantine',('lifecycle','revision','appointment_id','tenant_id','facility_id','patient_id')),
 'AppointmentTransitionMutation.java':('synqoria_appointment_reservations_quarantine',('lifecycle','revision','appointment_id','tenant_id','facility_id','patient_id')),
 'AppointmentLockedSnapshot.java':('synqoria_appointment_reservations_quarantine',('lifecycle','revision','appointment_id','tenant_id','facility_id','patient_id')),
 'AppointmentAuditStore.java':('synqoria_appointment_command_audit_quarantine',('event_id','tenant_id','facility_id','appointment_id','actor_pseudonym','appointment_revision','command_digest','previous_digest','audit_digest')),
 'AppointmentOutboxStore.java':('synqoria_appointment_outbox_quarantine',('event_id','tenant_id','facility_id','appointment_id','appointment_revision','event_type','payload_digest')),
 'AppointmentIdempotencyStore.java':('synqoria_appointment_idempotency_quarantine',('tenant_id','facility_id','actor_id','patient_id','key_digest','command_digest','state','appointment_id','result_revision','event_id')),
}
checks=0
for name,(table,columns) in contracts.items():
 source=(classes/name).read_text()
 assert table in source, (name,'table mismatch')
 assert f'CREATE TABLE IF NOT EXISTS {table} (' in sql,table
 block=sql.split(f'CREATE TABLE IF NOT EXISTS {table} (',1)[1].split('\n);',1)[0]
 for column in columns:
  assert re.search(r'(?m)^\s*'+column+r'\s+',block), (name,table,column)
  assert column in source, (name,'JDBC field missing',column)
  checks+=2
 checks+=2
for table in ['synqoria_appointment_reservations_quarantine','synqoria_appointment_command_audit_quarantine',
              'synqoria_appointment_outbox_quarantine','synqoria_appointment_idempotency_quarantine']:
 assert f'ALTER TABLE {table} ENABLE ROW LEVEL SECURITY' in sql
 assert f'ALTER TABLE {table} FORCE ROW LEVEL SECURITY' in sql
 assert f'REVOKE ALL ON {table} FROM PUBLIC' in sql
 checks+=3
assert not re.search(r'(?m)^\s*CREATE\s+POLICY\b',sql,re.I)
assert not re.search(r'(?m)^\s*GRANT\s+',sql,re.I)
assert 'clinical.appointments' not in (classes/'AppointmentRevisionUpdate.java').read_text()
assert 'appointment_reservations SET' not in (classes/'AppointmentScopedMutation.java').read_text()
checks+=4
print(f'PASS M02 schema/JDBC static alignment checks={checks}; live DB and restricted-role RLS unverified')
