"""Fail-closed source-level audit of quarantined encrypted triage persistence schema V017.
NOT an RLS role/privilege test: deployment stays forbidden without a real database.
"""
from pathlib import Path
import re,json
TABLES=(
 'synqoria_triage_secure_cases_quarantine',
 'synqoria_triage_secure_events_quarantine',
 'synqoria_triage_secure_outbox_quarantine',
 'synqoria_triage_secure_receipts_quarantine',
)
def audit(sql):
 text=Path(sql).read_text(encoding='utf-8')
 source=re.sub(r'--[^\n]*','',text)
 violations=[]
 for table in TABLES:
  if not re.search(r'\bCREATE\s+TABLE\s+'+table+r'\s*\(',source,re.I):violations.append(table+': missing table')
  for requirement in ('ENABLE ROW LEVEL SECURITY','FORCE ROW LEVEL SECURITY'):
   if not re.search(r'\bALTER\s+TABLE\s+'+table+r'\s+'+requirement+r'\s*;',source,re.I):violations.append(table+': '+requirement)
  if not re.search(r'\bREVOKE\s+ALL\s+ON\s+'+table+r'\s+FROM\s+PUBLIC\s*;',source,re.I):violations.append(table+': missing public revocation')
 if re.search(r'\bGRANT\s+|\bCREATE\s+POLICY\b|\bBYPASSRLS\b|\bDISABLE\s+ROW\s+LEVEL\s+SECURITY\b',source,re.I):violations.append('premature DB policy/grant/bypass')
 if not re.search(r'encrypted_snapshot\s+bytea\s+NOT\s+NULL',source,re.I):violations.append('missing encrypted case snapshot')
 if not re.search(r'PRIMARY\s+KEY\s*\(\s*tenant_id\s*,\s*facility_id\s*,\s*patient_id\s*,\s*case_id\s*\)',source,re.I):violations.append('missing scoped case key')
 for col in ('request_digest','event_digest','revision','command_id','patient_id','facility_id','tenant_id'):
  if not re.search(r'\b'+col+r'\b',source,re.I):violations.append('missing column '+col)
 if not re.search(r'CREATE\s+INDEX\s+triage_secure_unpublished_outbox_quarantine',source,re.I):violations.append('missing unpublished outbox index')
 if not re.search(r'FOREIGN\s+KEY\s*\(\s*tenant_id\s*,\s*facility_id\s*,\s*patient_id\s*,\s*case_id\s*,\s*command_id',source,re.I):violations.append('missing scoped event/outbox linkage')
 # No plaintext payload column should be created in this secure schema.
 if re.search(r'\b(?:metadata|observations|patient_notes|clinical_text)\s+(?:jsonb|text)\b',source,re.I):violations.append('unexpected plaintext clinical column')
 return {'ok':not violations,'violations':violations,'tables':len(TABLES),'mode':'static-quarantine-only'}
if __name__=='__main__':
 import sys
 r=audit(sys.argv[1]);print(json.dumps(r,sort_keys=True));sys.exit(0 if r['ok'] else 1)
