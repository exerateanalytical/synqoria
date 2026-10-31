from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
load=lambda f:json.loads((r/f).read_text())
ledger=load('registers/batch-ledger.json');ph=load('registers/phases.json')
assert ledger['version']==ph['version'] and tuple(map(int,ledger['version'].split('.'))) >= (0,24,0)
assert ledger['release_status']=='NOT_CERTIFIED'
report=load('evidence/batches/P02-P05-v240-ten-batches.json')
assert report['source_checkpoint']=='v0.23.0' and report['batches_passed']==report['batch_target']==10
expected={f'P05-B{i:04d}' for i in range(48,53)}|{f'P02-B{i:04d}' for i in range(78,83)}
assert {x['batch'] for x in report['results']}==expected
by_id={x['id']:x for x in ledger['batches']};assert len(by_id)==len(ledger['batches'])
for x in report['results']:
 assert x['verified'] and x['exit_code']==0
 assert x['batch'] in by_id
 row=by_id[x['batch']]
 assert row['status']=='implemented_unit_or_static_tested_NOT_production_integrated'
 assert not any(row[k] for k in ['clinical_screen_implementation','integration_tested','independently_reviewed','clinically_reviewed','accepted','external_certification'])
 assert 'PASS '+x['batch'] in (r/x['evidence_log']).read_text()
for phase in ph['phases']:
 assert phase['batches_completed']==sum(x['id'].startswith(phase['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches'])
 assert phase['certification']!='certified'
for f,n in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 d=load('registers/'+f);assert d['version']==ledger['version'] and len(d['items'])==n
 assert all(not x['implemented'] and not x['tested'] and not x['clinically_reviewed'] and not x['accepted'] for x in d['items'])
assert len(load('registers/canonical-modules.json')['module_register'])==40
assert len(load('registers/canonical-screens.json')['screen_register'])==261
for app in ['web','staff-mobile','patient-mobile']:
 d=load('apps/'+app+'/package.json');assert d['version']==ledger['version'] and d['dependencies']['@synqoria/ux-core']==ledger['version']
assert any(x['id']=='P05-B0027' for x in ledger['batches'])  # historical milestone, not current next batch
assert (r/'docs/authority/SYNQORIA_Platform_Architecture_v2.1.md').is_file()
assert (r/'docs/authority/SYNQORIA_UIUX_Master_v1.1.md').is_file()
print('PASS historical v0.24.0 evidence in current '+ledger['version']+' checkpoint; 40 modules; 261 screens; release denied')
