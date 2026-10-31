import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
load=lambda p:json.loads((r/p).read_text())
ledger=load('registers/batch-ledger.json');ph=load('registers/phases.json');ev=load('evidence/batches/P02-P05-v210-ten-batches.json')
assert ledger['version']==ph['version'] and tuple(map(int,ledger['version'].split('.'))) >= (0,21,0) and ledger['release_status']=='NOT_CERTIFIED'
expected={f'P02-B{x:04d}' for x in range(63,68)}|{f'P05-B{x:04d}' for x in range(33,38)}
assert len(ev['results'])==ev['batches_passed']==ev['batch_target']==10
assert {x['batch'] for x in ev['results']}==expected
ids={x['id']:x for x in ledger['batches']};assert len(ids)==len(ledger['batches'])
for item in ev['results']:
 assert item['exit_code']==0 and item['verified']
 assert f'exit=0 {item["batch"]}' in (r/item['evidence_log']).read_text()
 b=ids[item['batch']]
 assert b['status']=='implemented_unit_or_static_tested_NOT_production_integrated'
 assert not any(b[x] for x in ('clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'))
for p in ph['phases']:
 assert p['batches_completed']==sum(x['id'].startswith(p['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches']),(p['id'],p['batches_completed'])
 assert p['certification']!='certified'
for file,n in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 d=load('registers/'+file);assert d['version']==ledger['version'] and len(d['items'])==n
 assert all(not x['implemented'] and not x['tested'] and not x['clinically_reviewed'] and not x['accepted'] for x in d['items'])
assert 'P05-B0027' in ledger['next_batch']
assert len(load('registers/canonical-modules.json')['module_register'])==40
assert len(load('registers/canonical-screens.json')['screen_register'])==261
print('PASS v0.21.0: 10 individually evidenced source/unit batches, 40 modules, 261 unimplemented clinical screens, release locked')
