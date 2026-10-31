"""Fail-closed source/evidence test; cannot establish production clinical readiness."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
l=json.loads((r/'registers/batch-ledger.json').read_text())
p=json.loads((r/'registers/phases.json').read_text())
e=json.loads((r/'evidence/batches/P02-P03-v170-ten-batches.json').read_text())
assert l['version']==p['version'] and tuple(map(int,l['version'].split('.'))) >= (0,17,0)
assert e['checkpoint']=='v0.17.0' and e['base']=='v0.16.0'
assert e['batch_target']==e['batches_passed']==len(e['results'])==10
ids={x['id']:x for x in l['batches']}
assert len(ids)==len(l['batches'])
assert {x['batch'] for x in e['results']}==set([f'P03-B{x:04d}' for x in range(18,24)]+[f'P02-B{x:04d}' for x in range(44,48)])
for row in e['results']:
 assert row['verified'] and row['exit_code']==0
 assert (r/row['source']).is_file() and (r/row['evidence_log']).is_file()
 assert 'exit=0' in (r/row['evidence_log']).read_text()
 b=ids[row['batch']]
 assert b['status']=='implemented_unit_or_static_tested_NOT_integrated'
 assert not any(b[k] for k in ['clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'])
for phase in p['phases']:
 assert phase['batches_completed']==sum(b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked') for b in l['batches'])
 assert phase['certification']!='certified'
for f,count in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 x=json.loads((r/'registers'/f).read_text());assert x['version']==l['version'] and len(x['items'])==count
 assert all(not item['implemented'] and not item['tested'] and not item['clinically_reviewed'] and not item['accepted'] for item in x['items'])
assert l['release_status']=='NOT_CERTIFIED'
assert 'P05-B0027' in l['next_batch']
assert any(b['id']=='BLK-V170-PG' and b['status']=='open' for b in json.loads((r/'registers/blockers.json').read_text())['blockers'])
print('PASS v0.17.0 10 individually tested foundation batches, 40 modules, 261 locked screens, no clinical release')
