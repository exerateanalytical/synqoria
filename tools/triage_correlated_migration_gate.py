#!/usr/bin/env python3
"""Quarantined V019 static gate; strict source review, NOT PostgreSQL execution or RLS certification."""
from pathlib import Path
import re,json,sys
ROOT=Path(__file__).resolve().parents[1]
SQL=ROOT/'services/clinical-core/db/migration/V019__triage_secure_correlated_evidence_quarantine.sql'

def audit(sql:str):
    body=re.sub(r'--[^\n]*','',sql)
    violations=[]
    def need(pattern,label):
        if not re.search(pattern,body,re.I|re.S): violations.append('missing '+label)
    scope='tenant_id,facility_id,patient_id,case_id,command_id'
    for label in ('triage_secure_event_correlated_identity_quarantine','triage_secure_receipt_exact_event_fk_quarantine',
                  'triage_secure_event_outbox_key_quarantine','triage_secure_outbox_exact_event_fk_quarantine',
                  'triage_secure_genesis_operation_quarantine','triage_secure_genesis_previous_quarantine',
                  'triage_secure_receipts_append_only_quarantine'):
        need(r'\b'+label+r'\b',label)
    need(r'FOREIGN\s+KEY\s*\(\s*tenant_id\s*,\s*facility_id\s*,\s*patient_id\s*,\s*case_id\s*,\s*command_id\s*,\s*revision\s*,\s*event_digest\s*\)', 'scope+revision+digest receipt FK')
    need(r'FOREIGN\s+KEY\s*\(\s*tenant_id\s*,\s*facility_id\s*,\s*patient_id\s*,\s*case_id\s*,\s*command_id\s*,\s*event_digest\s*\)', 'scoped outbox digest FK')
    need(r'BEFORE\s+UPDATE\s+OR\s+DELETE\s+ON\s+synqoria_triage_secure_receipts_quarantine', 'append-only receipt enforcement')
    for table in ('synqoria_triage_secure_events_quarantine','synqoria_triage_secure_receipts_quarantine','synqoria_triage_secure_outbox_quarantine'):
        for clause in ('ENABLE ROW LEVEL SECURITY','FORCE ROW LEVEL SECURITY'):
            need(r'ALTER\s+TABLE\s+'+table+r'\s+'+clause+r'\s*;', table+' '+clause)
        need(r'REVOKE\s+ALL\s+ON\s+'+table+r'\s+FROM\s+PUBLIC',table+' PUBLIC revocation')
    for forbidden in (r'\bGRANT\b',r'\bCREATE\s+POLICY\b',r'\bBYPASSRLS\b',r'\bSECURITY\s+DEFINER\b',
                      r'\bDISABLE\s+ROW\s+LEVEL\s+SECURITY\b',r'\bDROP\s+TABLE\b'):
        if re.search(forbidden,body,re.I):violations.append('forbidden operation '+forbidden)
    return {'passed':not violations,'violations':violations,'type':'static quarantined SQL contract only'}

if __name__=='__main__':
    result=audit(SQL.read_text());print(json.dumps(result,sort_keys=True));sys.exit(0 if result['passed'] else 1)
