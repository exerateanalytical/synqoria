"""Fail-closed checkpoint assertions for v0.15.0; not clinical certification."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
e=json.loads((r/'evidence/batches/P01-P05-v150-ten-batches.json').read_text())
l=json.loads((r/'registers/batch-ledger.json').read_text())
p=json.loads((r/'registers/phases.json').read_text())
s=json.loads((r/'registers/screen-status-matrix.json').read_text())
assert l['version']==p['version'] and tuple(map(int,l['version'].split('.'))) >= (0,15,0) and e['checkpoint']=='v0.15.0'
assert e['batch_target']==e['batches_passed']==10
assert len({x['batch'] for x in e['results']})==10
assert all(x['verified'] and x['exit_code']==0 for x in e['results'])
ids={x['id']:x for x in l['batches']}
for item in e['results']:
 b=ids[item['batch']]
 assert b['status'].startswith('implemented_unit_or_static_tested')
 assert not any(b[k] for k in ['clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'])
 assert (r/item['evidence_log']).exists()
assert len(s['items'])==261
assert all(not item.get('implemented',False) and not item.get('accepted',False) for item in s['items'])
assert all(x['certification']=='not_certified' for x in p['phases'] if x['id'] in ['P01','P02','P05'])
assert ids['P01-B0002']['status']=='blocked_attempt_not_completed'
assert l['release_status']=='NOT_CERTIFIED'
print('PASS v0.15.0 10 unique verified non-clinical increments, 261 unimplemented screens, no release claims')
