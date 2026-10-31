"""Source/evidence verification only; does not certify a clinical or production release."""
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
load=lambda p:json.loads((r/p).read_text())
l=load('registers/batch-ledger.json');p=load('registers/phases.json');e=load('evidence/batches/P02-P03-v180-ten-batches.json')
assert l['version']==p['version'] and tuple(map(int,l['version'].split('.'))) >= (0,18,0)
assert e['checkpoint']=='v0.18.0' and e['base']=='v0.17.0'
assert e['batch_target']==e['batches_passed']==len(e['results'])==10
expected={f'P03-B{x:04d}' for x in range(24,29)}|{f'P02-B{x:04d}' for x in range(48,53)}
assert {x['batch'] for x in e['results']}==expected
rows={x['id']:x for x in l['batches']};assert len(rows)==len(l['batches'])
for x in e['results']:
 assert x['verified'] and x['exit_code']==0
 assert (r/x['source']).is_file()
 log=(r/x['evidence_log']).read_text();assert 'exit=0' in log and x['batch'] in log
 row=rows[x['batch']];assert row['status']=='implemented_unit_or_static_tested_NOT_integrated'
 assert all(not row[k] for k in ('clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'))
for phase in p['phases']:
 assert phase['batches_completed']==sum(x['id'].startswith(phase['id']+'-') and not x['status'].startswith('blocked') for x in l['batches'])
 assert phase['certification']!='certified'
for filename,count in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 x=load('registers/'+filename);assert x['version']==l['version'] and len(x['items'])==count
 assert all(not row['implemented'] and not row['tested'] and not row['clinically_reviewed'] and not row['accepted'] for row in x['items'])
assert l['release_status']=='NOT_CERTIFIED' and 'P05-B0027' in l['next_batch']
assert any(b['id']=='BLK-V180-OIDC-BINDING' and b['status']=='open' for b in load('registers/blockers.json')['blockers'])
assert 'lockedRegistrationJourney(locale).map(' in (r/'apps/staff-mobile/RegistrationPreview.tsx').read_text()
assert all('lockedNavigationAccessibility' in (r/f'apps/{app}/App.tsx').read_text() for app in ('staff-mobile','patient-mobile'))
print('PASS v0.18.0: 10 separately tested nonclinical increments; 40 modules, 261 unimplemented screens; release locked')
