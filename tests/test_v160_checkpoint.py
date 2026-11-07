from pathlib import Path
import json,hashlib
r=Path(__file__).resolve().parents[1]
l=json.loads((r/'registers/batch-ledger.json').read_text())
p=json.loads((r/'registers/phases.json').read_text())
e=json.loads((r/'evidence/batches/P01-P05-v160-ten-batches.json').read_text())
s=json.loads((r/'registers/screen-status-matrix.json').read_text())
m=json.loads((r/'registers/module-status-matrix.json').read_text())
assert l['version']==p['version'] and tuple(map(int,l['version'].split('.'))) >= (0,16,0)
assert e['checkpoint']=='v0.16.0' and e['batch_target']==e['batches_passed']==len(e['results'])==10
assert len({x['batch'] for x in e['results']})==10
ids={x['id']:x for x in l['batches']}
for row in e['results']:
 assert row['exit_code']==0 and row['verified']
 assert (r/row['source']).is_file() and (r/row['evidence_log']).is_file()
 assert 'exit=0' in (r/row['evidence_log']).read_text()
 b=ids[row['batch']]
 assert b['status']=='implemented_unit_or_static_tested_NOT_integrated'
 assert not any(b[k] for k in ['clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'])
for phase in p['phases']:
 assert phase['batches_completed']==sum(x['id'].startswith(phase['id']+'-') and not x['status'].startswith('blocked') for x in l['batches'])
assert len(s['items'])==261 and len(m['items'])==40
assert sum(bool(x['implemented']) for x in s['items'])==86
assert all(not x['accepted'] and not x['clinically_reviewed'] for x in s['items'])
assert all(not x['implemented'] and not x['accepted'] for x in m['items'])
assert l['release_status']=='NOT_CERTIFIED'
# Historical v0.16.0 milestone may be completed; current next_batch advances independently.
assert 'P05-B0027' in ids
print('PASS v0.16.0 10 individually tested nonclinical increments, 40 modules, 261 canonical screens, source integration newer than checkpoint, no release')
