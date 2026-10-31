"""Static M03 encrypted-snapshot migration review gate, not a PostgreSQL execution result."""
from pathlib import Path
import re

TABLES=('synqoria_triage_cases_quarantine','synqoria_triage_events_quarantine','synqoria_triage_outbox_quarantine')

def audit(source):
    text=Path(source).read_text(encoding='utf-8')
    clean='\n'.join(line.split('--')[0] for line in text.splitlines())
    if re.search(r'\b(?:GRANT|CREATE\s+POLICY|BYPASSRLS|ALTER\s+ROLE|SET\s+ROLE)\b',clean,re.I):
        raise ValueError('quarantine migration must not grant access')
    for table in TABLES:
        for sql in (f'ALTER TABLE {table} ENABLE ROW LEVEL SECURITY',f'ALTER TABLE {table} FORCE ROW LEVEL SECURITY',f'REVOKE ALL ON {table} FROM PUBLIC'):
            if sql not in clean: raise ValueError('missing RLS fence: '+sql)
    for token in ('snapshot_ciphertext bytea','snapshot_key_id text','snapshot_digest text',
                  'triage_snapshot_atomic_null_quarantine','triage_snapshot_digest_format_quarantine',
                  'triage_snapshot_size_quarantine','triage_snapshot_key_format_quarantine',
                  'triage_events_append_only_quarantine','triage_outbox_append_only_quarantine',
                  "RAISE EXCEPTION 'triage audit and outbox records are append-only'",'BEFORE UPDATE OR DELETE'):
        if token not in clean: raise ValueError('missing snapshot or append-only control: '+token)
    if clean.count('BEFORE UPDATE OR DELETE')!=2: raise ValueError('both event and outbox append-only triggers required')
    if not re.search(r'ALTER\s+TABLE\s+synqoria_triage_cases_quarantine\s+ADD\s+COLUMN',clean,re.I):
        raise ValueError('snapshot columns not applied to scoped cases')
    return {'status':'quarantined_static_contract_only','rls_tables':len(TABLES),'append_only_triggers':2}
