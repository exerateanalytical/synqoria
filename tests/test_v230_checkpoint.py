import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
load=lambda p:json.loads((r/p).read_text())
ledger=load('registers/batch-ledger.json');ph=load('registers/phases.json')
assert ledger['version']==ph['version']=='0.23.0'
assert ledger['release_status']=='NOT_CERTIFIED'
ev=load('evidence/batches/P02-P05-v230-ten-batches.json')
assert ev['source_checkpoint']=='v0.22.0' and ev['checkpoint']=='v0.23.0'
assert ev['batch_target']==ev['batches_passed']==len(ev['results'])==10
expected={f'P05-B{i:04d}' for i in range(43,48)}|{f'P02-B{i:04d}' for i in range(73,78)}
assert {row['batch'] for row in ev['results']}==expected
by_id={x['id']:x for x in ledger['batches']}
assert len(by_id)==len(ledger['batches'])
for row in ev['results']:
 assert row['exit_code']==0 and row['verified']
 assert row['batch'] in by_id
 b=by_id[row['batch']]
 assert b['status']=='implemented_unit_or_static_tested_NOT_production_integrated'
 assert not any(b[k] for k in ('clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'))
 log=(r/row['evidence_log']).read_text()
 if row['batch'].startswith('P05'):assert 'PASS '+row['batch'] in log
 else:assert '# pass 1' in log and '# fail 0' in log
for phase in ph['phases']:
 assert phase['batches_completed']==sum(x['id'].startswith(phase['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches'])
 assert phase['certification']!='certified'
for name,n in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 d=load('registers/'+name);assert d['version']=='0.23.0' and len(d['items'])==n
 assert all(not x['implemented'] and not x['tested'] and not x['clinically_reviewed'] and not x['accepted'] for x in d['items'])
assert len(load('registers/canonical-modules.json')['module_register'])==40
assert len(load('registers/canonical-screens.json')['screen_register'])==261
assert 'P05-B0027' in ledger['next_batch']
for app in ['web','staff-mobile','patient-mobile']:
 d=load('apps/'+app+'/package.json')
 assert d['version']=='0.23.0' and d['dependencies']['@synqoria/ux-core']=='0.23.0'
print('PASS v0.23.0: 10 verified batches, 40 modules, 261 screens, no clinical certification')
