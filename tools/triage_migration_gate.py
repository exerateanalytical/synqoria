"""M03 quarantined triage SQL safety source gate; not a live PostgreSQL enforcement check."""
from pathlib import Path
import re

TABLES=("synqoria_triage_cases_quarantine","synqoria_triage_events_quarantine","synqoria_triage_outbox_quarantine")

def audit(source):
    clean='\n'.join(line.split('--')[0] for line in Path(source).read_text().splitlines())
    found=re.findall(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z][a-z0-9_]*)',clean,re.I)
    if set(found)!=set(TABLES) or len(found)!=3: raise ValueError('triage tables unexpected')
    if re.search(r'\b(?:GRANT|CREATE\s+POLICY|BYPASSRLS|SET\s+ROLE|ALTER\s+ROLE)\b',clean,re.I):
        raise ValueError('security boundary cannot be enabled via draft SQL')
    for table in TABLES:
        for fragment in (f'ALTER TABLE {table} ENABLE ROW LEVEL SECURITY',
                         f'ALTER TABLE {table} FORCE ROW LEVEL SECURITY',
                         f'REVOKE ALL ON {table} FROM PUBLIC'):
            if fragment not in clean: raise ValueError('missing security fence: '+fragment)
        table_def=re.search(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?'+table+r'\s*\((.*?)\)\s*;',clean,re.I|re.S)
        if table_def is None:raise ValueError('missing triage definition '+table)
        for col in ('tenant_id','facility_id','patient_id'):
            if not re.search(r'\b'+col+r'\s+uuid\s+NOT\s+NULL\b',table_def.group(1),re.I):
                raise ValueError('missing patient scope '+table+'.'+col)
    for token in ('triage_event_revision_unique','triage_event_command_unique','triage_outbox_event_unique','triage_event_case FOREIGN KEY', 'triage_outbox_case FOREIGN KEY','triage_outbox_event_chain FOREIGN KEY'):
        if token not in clean:raise ValueError('missing concurrency/outbox proof '+token)
    return {'status':'source_quarantine_verified_only','tables':list(TABLES)}
if __name__=='__main__':
    import sys,json
    print(json.dumps(audit(sys.argv[1]),indent=2))
