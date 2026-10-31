from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
load=lambda x:json.loads((r/x).read_text())
ledger=load('registers/batch-ledger.json');phases=load('registers/phases.json');report=load('evidence/batches/P01-P02-P05-v250-ten-batches.json')
assert ledger['version']==phases['version']=='0.25.0'
assert report['source_checkpoint']=='v0.24.0' and report['batch_target']==report['batches_passed']==10
assert len(report['results'])==10 and all(x['verified'] and x['exit_code']==0 for x in report['results'])
assert len({x['batch'] for x in report['results']})==10
assert {x['batch'] for x in report['results']}==({f'P01-B{i:04d}' for i in range(33,36)}|{f'P02-B{i:04d}' for i in range(84,86)}|{f'P05-B{i:04d}' for i in range(53,58)})
for x in report['results']:
 assert (r/x['evidence_log']).exists()
 assert x['batch'] in (r/x['evidence_log']).read_text()
byid={x['id']:x for x in ledger['batches']};assert len(byid)==len(ledger['batches'])
for x in report['results']:
 b=byid[x['batch']];assert b['status'].startswith('implemented')
 assert not any(b[k] for k in ('clinical_screen_implementation','integration_tested','independently_reviewed','clinically_reviewed','accepted','external_certification'))
for x in ('P02-B0083','P05-B0027'):
 assert byid[x]['status'].startswith('blocked')
for p in phases['phases']:
 assert p['batches_completed']==sum(x['id'].startswith(p['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches'])
 assert p['certification']!='certified'
for f,count in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 m=load('registers/'+f);assert m['version']=='0.25.0' and len(m['items'])==count
 assert all(not x['implemented'] and not x['tested'] and not x['clinically_reviewed'] and not x['accepted'] for x in m['items'])
assert len(load('registers/canonical-modules.json')['module_register'])==40
assert len(load('registers/canonical-screens.json')['screen_register'])==261
for app in ('web','staff-mobile','patient-mobile'):
 d=load('apps/'+app+'/package.json');assert d['version']=='0.25.0' and d['dependencies']['@synqoria/ux-core']=='0.25.0'
for f in ('docs/authority/SYNQORIA_Platform_Architecture_v2.1.md','docs/authority/SYNQORIA_UIUX_Master_v1.1.md'):
 assert (r/f).exists()
for f in ('v250-regression-core.json','v250-regression-browser.json'):
 d=load('evidence/batches/'+f);assert d['expected_gates_met']
print('PASS v0.25.0: 10 individually tested, 40 modules/261 screens preserved, blocked gates denied')
