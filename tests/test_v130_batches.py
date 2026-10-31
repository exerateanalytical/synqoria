from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
l=json.loads((r/'registers/batch-ledger.json').read_text())
p=json.loads((r/'registers/phases.json').read_text())
e=json.loads((r/'evidence/batches/P01-P05-v130-ten-batches.json').read_text())
assert l['version']==p['version'] and tuple(map(int,l['version'].split('.'))) >= (0,13,0) and l['release_status']=='NOT_CERTIFIED'
assert e['batch_target']==e['batches_passed']==len(e['results'])==10
assert len({x['batch'] for x in e['results']})==10
assert len({x['id'] for x in l['batches']})==len(l['batches'])
for x in e['results']:
 b=next(b for b in l['batches'] if b['id']==x['batch'])
 assert x['verified'] and x['exit_code']==0 and all(c['exit_code']==0 for c in x['commands'])
 assert b['status']=='implemented_tested_not_production_integrated'
 assert not any(b[k] for k in ('clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'))
 assert all((r/f).is_file() for f in x['source_files'])
for phase in p['phases']:
 assert phase['batches_completed']==sum(b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked') for b in l['batches'])
assert len(json.loads((r/'registers/canonical-screens.json').read_text())['screen_register'])==261
assert all(s['code_status']=='not_implemented' for s in json.loads((r/'registers/canonical-screens.json').read_text())['screen_register'])
assert len(json.loads((r/'registers/canonical-modules.json').read_text())['module_register'])==40
print('PASS v0.13.0: 10 tested increments, canonical inventory and fail-closed clinical gates')
