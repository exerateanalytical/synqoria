"""Verify new batch ledger and clinical lock without asserting any external acceptance."""
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
load=lambda p:json.loads((r/p).read_text())
ledger=load('registers/batch-ledger.json');phase=load('registers/phases.json');e=load('evidence/batches/P02-P03-v200-ten-batches.json')
assert ledger['version']==phase['version'] in ('0.20.0','0.21.0','0.22.0','0.23.0')
assert e['batch_target']==e['batches_passed']==len(e['results'])==10
expected={f'P02-B{x:04d}' for x in range(58,63)}|{f'P03-B{x:04d}' for x in range(29,34)}
assert {x['batch'] for x in e['results']}==expected
ids={x['id']:x for x in ledger['batches']};assert len(ids)==len(ledger['batches'])
for result in e['results']:
 assert result['verified'] and result['exit_code']==0
 assert 'exit=0 '+result['batch'] in (r/result['evidence_log']).read_text()
 b=ids[result['batch']]
 assert b['status']=='implemented_unit_or_static_tested_NOT_production_integrated'
 assert not any(b[x] for x in ('clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'))
for p in phase['phases']:
 assert p['batches_completed']==sum(b['id'].startswith(p['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
 assert p['certification']!='certified'
for name,n in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 d=load('registers/'+name);assert d['version'] in ('0.20.0','0.21.0','0.22.0','0.23.0') and len(d['items'])==n
 assert all(not x['implemented'] and not x['tested'] and not x['clinically_reviewed'] and not x['accepted'] for x in d['items'])
assert ledger['release_status']=='NOT_CERTIFIED' and 'P05-B0027' in ledger['next_batch']
assert len(load('registers/canonical-modules.json')['module_register'])==40
assert len(load('registers/canonical-screens.json')['screen_register'])==261
print('PASS v0.20.0 ten individually evidenced foundations; 40 modules/261 screens unchanged and clinical release locked')
