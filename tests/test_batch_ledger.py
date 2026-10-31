"""Ensure ten actual evidence records match phase counters; no certification drift."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
ledger=json.loads((root/'registers/batch-ledger.json').read_text())
phases=json.loads((root/'registers/phases.json').read_text())['phases']
evidence=json.loads((root/'evidence/batches/P01-P04-v050-ten-batches.json').read_text())
assert evidence['batch_target']==10 and evidence['batches_passed']==10
ids=[b['id'] for b in ledger['batches']]
assert len(ids)==len(set(ids))
assert all(e['batch'] in ids and e['verified'] and e['exit_code']==0 for e in evidence['results'])
for e in evidence['results']:
 b=next(b for b in ledger['batches'] if b['id']==e['batch'])
 assert not b['external_certification'] and not b['accepted'] and not b['clinically_reviewed']
for phase in phases:
 if phase['id'] in ('P01','P02','P03','P04'):
  assert phase['batches_completed']==len([b for b in ledger['batches'] if b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked')])
  assert phase['certification']=='not_certified'
assert ledger['release_status']=='NOT_CERTIFIED'
assert any(b['id']=='P01-B0002' and b['status']=='blocked_attempt_not_completed' for b in ledger['batches'])
print('PASS: ten independent batch evidence records, monotonic phase counts, no false certification')
