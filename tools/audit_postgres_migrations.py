#!/usr/bin/env python3
"""Offline migration safety audit. Not a PostgreSQL execution or approval."""
from pathlib import Path
import re, json, sys
ROOT = Path(__file__).resolve().parents[1]
CRITICAL = {
    'hospital_facilities', 'clinical_outbox', 'clinical_inbox',
    'patient_registration_review_decisions', 'patient_registration_review_events',
    'patient_registration_audit', 'patient_registration_command_claim',
}

def audit(directory: Path):
    issues=[]
    files=sorted(directory.glob('V[0-9][0-9][0-9]__*.sql'))
    nums=[int(p.name[1:4]) for p in files]
    if not files or nums!=list(range(1,len(files)+1)):
        issues.append('missing_or_duplicate_migration_version')
    sql='\n'.join(p.read_text(encoding='utf-8') for p in files)
    code='\n'.join(re.sub(r'--[^\n]*','',p.read_text(encoding='utf-8')) for p in files)
    if re.search(r'\bGRANT\s+\w+\s+ON\b|\bCREATE\s+POLICY\b|\bALTER\s+ROLE\b|\bDROP\s+TABLE\b',code,re.I):
        issues.append('unsafe_authority_or_destructive_statement')
    created=set(re.findall(r'\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z][a-z0-9_]*)\s*\(',code,re.I))
    for table in sorted(CRITICAL):
        if table not in created:issues.append('missing_table:'+table);continue
        for kind in ('ENABLE','FORCE'):
            if not re.search(r'\bALTER\s+TABLE\s+'+table+r'\s+'+kind+r'\s+ROW\s+LEVEL\s+SECURITY\b',code,re.I):
                issues.append('missing_'+kind.lower()+'_rls:'+table)
        # grouped REVOKE statements are valid, provided this table is named in their list
        revoked=any(re.search(r'\b'+table+r'\b',m.group(1),re.I) for m in re.finditer(r'\bREVOKE\s+ALL\s+ON\s+(.+?)\s+FROM\s+PUBLIC\s*;',code,re.I|re.S))
        if not revoked:issues.append('missing_public_revoke:'+table)
    return {'files':[p.name for p in files], 'critical_tables':len(CRITICAL), 'issues':issues,'passed':not issues,
            'scope':'static SQL audit only; PostgreSQL parsing, permissions and RLS execution NOT tested'}

if __name__=='__main__':
    result=audit(Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'services/clinical-core/db/migration')
    print(json.dumps(result,indent=2));sys.exit(0 if result['passed'] else 2)
