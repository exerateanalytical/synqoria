"""Evidence integrity only, NOT a clinical readiness assertion."""
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
load=lambda name:json.loads((r/name).read_text())
l=load('registers/batch-ledger.json');p=load('registers/phases.json');e=load('evidence/batches/P02-P05-v190-ten-batches.json')
assert l['version']==p['version'] in ('0.19.0','0.20.0','0.21.0','0.22.0','0.23.0')
assert e['checkpoint']=='v0.19.0' and e['base']=='v0.18.0'
assert e['batch_target']==e['batches_passed']==len(e['results'])==10
expected={f'P05-B{x:04d}' for x in range(28,33)}|{f'P02-B{x:04d}' for x in range(53,58)}
assert {x['batch'] for x in e['results']}==expected
ids={x['id']:x for x in l['batches']};assert len(ids)==len(l['batches'])
for x in e['results']:
 assert x['verified'] and x['exit_code']==0 and (r/x['source']).is_file()
 assert 'exit=0 '+x['batch'] in (r/x['evidence_log']).read_text()
 b=ids[x['batch']];assert b['status']=='implemented_unit_or_static_tested_NOT_integrated'
 assert not any(b[k] for k in ['clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'])
for phase in p['phases']:
 assert phase['batches_completed']==sum(x['id'].startswith(phase['id']+'-') and not x['status'].startswith('blocked') for x in l['batches'])
 assert phase['certification']!='certified'
for name,n in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 d=load('registers/'+name);assert d['version'] in ('0.19.0','0.20.0','0.21.0','0.22.0','0.23.0') and len(d['items'])==n
 assert all(not x['implemented'] and not x['tested'] and not x['clinically_reviewed'] and not x['accepted'] for x in d['items'])
assert l['release_status']=='NOT_CERTIFIED' and 'P05-B0027' in l['next_batch']
assert any(x['id']=='BLK-V190-REVIEW-PERSISTENCE' and x['status']=='open' for x in load('registers/blockers.json')['blockers'])
assert 'registrationReleasePresentation' in (r/'apps/staff-mobile/RegistrationPreview.tsx').read_text()
assert len(load('registers/canonical-modules.json')['module_register'])==40
assert len(load('registers/canonical-screens.json')['screen_register'])==261
print('PASS v0.19.0 10 independently tested components, 40 modules, 261 unimplemented screens; release locked')
