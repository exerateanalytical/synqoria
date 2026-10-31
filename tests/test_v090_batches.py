"""Confirm ten new real code/test increments and no release-acceptance inflation."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
ledger=json.loads((root/'registers/batch-ledger.json').read_text());phases=json.loads((root/'registers/phases.json').read_text())['phases']
batches=json.loads((root/'evidence/batches/P01-P04-v090-ten-batches.json').read_text())
assert batches['batch_target']==batches['batches_passed']==10
assert len(batches['results'])==10 and len({b['batch'] for b in batches['results']})==10
assert all(b['verified'] and b['exit_code']==0 and b['not_clinically_certified'] for b in batches['results'])
for record in batches['results']:
 row=next(x for x in ledger['batches'] if x['id']==record['batch'])
 assert not row['clinical_screen_implementation'] and not row['accepted'] and not row['clinically_reviewed']
 assert row['status']=='implemented_tested_not_production_integrated'
 assert all((root/file).exists() for file in record['source_files'])
for phase in phases:
 if phase['id'] in ('P01','P02','P03','P04'):
  assert phase['batches_completed']==len([b for b in ledger['batches'] if b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked')])
  assert phase['certification']=='not_certified'
assert tuple(map(int,ledger['version'].split('.'))) >= (0,9,0) and ledger['release_status']=='NOT_CERTIFIED'
print('PASS: 10 evidence-verified new batch IDs, no fictitious screen/certification completion')
