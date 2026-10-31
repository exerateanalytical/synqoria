#!/usr/bin/env python3
"""Fail-closed STATIC review of V018. Never substitutes for executing PostgreSQL/RLS tests."""
from pathlib import Path
import re,hashlib,json,sys
ROOT=Path(__file__).resolve().parents[1]
SQL=ROOT/'services/clinical-core/db/migration/V018__triage_secure_genesis_quarantine.sql'
LOCK=ROOT/'registers/migration-sha256-lock.json'

def audit(source):
 text=re.sub(r'--[^\n]*','',source)
 v=[]
 def require(pattern,reason):
  if not re.search(pattern,text,re.I|re.S):v.append(reason)
 require(r'DROP\s+CONSTRAINT\s+IF\s+EXISTS\s+synqoria_triage_secure_events_quarantine_revision_check\s*;', 'old revision constraint not dropped')
 require(r'ADD\s+CONSTRAINT\s+triage_secure_event_revision_quarantine\s+CHECK\s*\(\s*revision\s*>=\s*1\s*\)', 'genesis revision 1 not allowed')
 require(r'ADD\s+CONSTRAINT\s+triage_secure_receipt_event_fk_quarantine\s+FOREIGN\s+KEY\s*\(\s*tenant_id\s*,\s*facility_id\s*,\s*patient_id\s*,\s*case_id\s*,\s*command_id\s*\)', 'scoped receipt event FK missing')
 require(r'REFERENCES\s+synqoria_triage_secure_events_quarantine\s*\(\s*tenant_id\s*,\s*facility_id\s*,\s*patient_id\s*,\s*case_id\s*,\s*command_id\s*\)', 'scoped event FK target missing')
 require(r'CREATE\s+TRIGGER\s+triage_secure_events_append_only_quarantine\s+BEFORE\s+UPDATE\s+OR\s+DELETE\s+ON\s+synqoria_triage_secure_events_quarantine', 'append-only event trigger missing')
 require(r'RAISE\s+EXCEPTION', 'event mutation rejection missing')
 for table in ('synqoria_triage_secure_events_quarantine','synqoria_triage_secure_receipts_quarantine'):
  for clause in ('ENABLE ROW LEVEL SECURITY','FORCE ROW LEVEL SECURITY'):
   require(r'ALTER\s+TABLE\s+'+table+r'\s+'+clause+r'\s*;',table+' '+clause+' missing')
  require(r'REVOKE\s+ALL\s+ON\s+'+table+r'\s+FROM\s+PUBLIC\s*;',table+' public revocation missing')
 for forbidden in (r'\bGRANT\b',r'\bCREATE\s+POLICY\b',r'\bBYPASSRLS\b',r'\bSECURITY\s+DEFINER\b',r'\bDROP\s+TABLE\b',r'\bDISABLE\s+ROW\s+LEVEL\s+SECURITY\b'):
  if re.search(forbidden,text,re.I):v.append('forbidden unsafe migration operation: '+forbidden)
 return {'ok':not v,'violations':v,'scope':'V018 static quarantine review only'}

MUTATIONS={
 'P06-B0269':('DROP CONSTRAINT IF EXISTS synqoria_triage_secure_events_quarantine_revision_check;','', 'old revision'),
 'P06-B0270':('CHECK (revision >= 1)','CHECK (revision > 1)','genesis revision'),
 'P06-B0271':('FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id)','FOREIGN KEY (command_id)','scoped receipt'),
 'P06-B0272':('REFERENCES synqoria_triage_secure_events_quarantine (tenant_id,facility_id,patient_id,case_id,command_id)','REFERENCES synqoria_triage_secure_events_quarantine (command_id)','scoped event'),
 'P06-B0273':('BEFORE UPDATE OR DELETE ON synqoria_triage_secure_events_quarantine','BEFORE INSERT ON synqoria_triage_secure_events_quarantine','append-only'),
 'P06-B0274':("RAISE EXCEPTION 'triage audit events are append-only';",'NULL;','event mutation'),
 'P06-B0275':('FORCE ROW LEVEL SECURITY;','', 'FORCE ROW LEVEL SECURITY'),
 'P06-B0276':('REVOKE ALL ON synqoria_triage_secure_events_quarantine FROM PUBLIC;','', 'public revocation'),
 'P06-B0277':('-- No CREATE POLICY / GRANT / BYPASSRLS. This migration cannot enable a clinical deployment.','GRANT ALL ON synqoria_triage_secure_events_quarantine TO PUBLIC;', 'forbidden'),
}

def batch(identifier):
 source=SQL.read_text(encoding='utf8')
 base=audit(source)
 if not base['ok']:raise AssertionError('base migration invalid: '+str(base['violations']))
 if identifier=='P06-B0278':
  lock=json.loads(LOCK.read_text())
  digest=hashlib.sha256(SQL.read_bytes()).hexdigest()
  assert lock['files'][SQL.name]==digest, 'V018 migration checksum lock missing or incorrect'
  assert len(lock['files'])==18, 'migration inventory expected 18 locked files'
  return {'id':identifier,'pass':True,'check':'new immutable migration checksum lock','sha256':digest}
 old,new,expected=MUTATIONS[identifier]
 assert old in source, 'mutation target absent: '+old
 mutated=source.replace(old,new,1)
 result=audit(mutated)
 assert not result['ok'] and any(expected in v for v in result['violations']), 'adversarial mutation escaped gate: '+str(result)
 return {'id':identifier,'pass':True,'check':'negative SQL mutation rejected','expected_violation':expected}

if __name__=='__main__':
 if len(sys.argv)==3 and sys.argv[1]=='--batch':
  print(json.dumps(batch(sys.argv[2]),sort_keys=True))
 elif len(sys.argv)==1:
  result=audit(SQL.read_text());print(json.dumps(result,sort_keys=True));sys.exit(0 if result['ok'] else 1)
 else:raise SystemExit('usage: triage_genesis_migration_gate.py [--batch P06-B0269..0278]')
