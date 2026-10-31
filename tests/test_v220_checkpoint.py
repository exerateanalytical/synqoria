import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
load=lambda p:json.loads((r/p).read_text())
ledger=load('registers/batch-ledger.json');ph=load('registers/phases.json');ev=load('evidence/batches/P02-P05-v220-ten-batches.json')
assert ledger['version']==ph['version'] and tuple(map(int,ledger['version'].split('.'))) >= (0,22,0) and ledger['release_status']=='NOT_CERTIFIED'
assert ev['checkpoint']=='v0.22.0' and ev['source_checkpoint']=='v0.21.0'
assert ev['batch_target']==ev['batches_passed']==len(ev['results'])==10
expected={f'P05-B{x:04d}' for x in range(38,43)}|{f'P02-B{x:04d}' for x in range(68,73)}
assert {x['batch'] for x in ev['results']}==expected
batches={x['id']:x for x in ledger['batches']}
assert len(batches)==len(ledger['batches'])
for row in ev['results']:
 assert row['exit_code']==0 and row['verified']
 log=(r/row['evidence_log']).read_text();assert 'exit=0 '+row['batch'] in log
 if row['batch'].startswith('P05'):assert 'PASS '+row['batch'] in log
 else:assert '# pass 1' in log and '# fail 0' in log
 b=batches[row['batch']]
 assert b['status']=='implemented_unit_or_static_tested_NOT_production_integrated'
 assert not any(b[x] for x in ('clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'))
for phase in ph['phases']:
 assert phase['batches_completed']==sum(b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
 assert phase['certification']!='certified'
for f,n in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 d=load('registers/'+f);assert d['version']==ledger['version'] and len(d['items'])==n
 assert all(not x['implemented'] and not x['tested'] and not x['clinically_reviewed'] and not x['accepted'] for x in d['items'])
assert len(load('registers/canonical-modules.json')['module_register'])==40
assert len(load('registers/canonical-screens.json')['screen_register'])==261
assert 'P05-B0027' in ledger['next_batch']
print('PASS v0.22.0: 10 individual batches, 40 modules, 261 clinical screens still unimplemented, release locked')
