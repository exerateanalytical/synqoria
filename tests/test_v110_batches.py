"""Verify ten specific implementation records and preserve strict release boundaries."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
l=json.loads((root/'registers/batch-ledger.json').read_text())
p=json.loads((root/'registers/phases.json').read_text())
a=json.loads((root/'evidence/batches/P01-P04-v110-ten-batches.json').read_text())
assert l['version']==p['version'] and tuple(map(int,l['version'].split('.'))) >= (0,11,0)
assert l['release_status']=='NOT_CERTIFIED'
assert a['batch_target']==a['batches_passed']==len(a['results'])==10
assert len({r['batch'] for r in a['results']})==10
assert len({r['id'] for r in l['batches']})==len(l['batches'])
for r in a['results']:
 item=next(i for i in l['batches'] if i['id']==r['batch'])
 assert r['verified'] and r['exit_code']==0 and r['not_clinically_certified']
 assert item['status']=='implemented_tested_not_production_integrated'
 assert all(not item[field] for field in ('accepted','clinically_reviewed','external_certification','clinical_screen_implementation'))
 assert all((root/f).is_file() for f in r['source_files'])
for phase in p['phases']:
 if phase['id'] in ('P01','P02','P03','P04'):
  assert phase['batches_completed']==len([x for x in l['batches'] if x['id'].startswith(phase['id']+'-') and not x['status'].startswith('blocked')])
  assert phase['certification']=='not_certified'
print('PASS v0.11.0: 10 distinct implemented+tested source records; no clinical or release certification')
