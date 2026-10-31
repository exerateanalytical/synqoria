from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
ledger=json.loads((r/'registers/batch-ledger.json').read_text()); phases=json.loads((r/'registers/phases.json').read_text()); details=json.loads((r/'evidence/batches/P01-P05-v120-ten-batches.json').read_text()); screens=json.loads((r/'registers/canonical-screens.json').read_text())['screen_register']
assert ledger['version']==phases['version'] and tuple(map(int,ledger['version'].split('.'))) >= (0,12,0)
assert details['batch_target']==details['batches_passed']==len(details['results'])==10
assert len({b['id'] for b in ledger['batches']})==len(ledger['batches'])
for row in details['results']:
 batch=next(x for x in ledger['batches'] if x['id']==row['batch'])
 assert row['verified'] and row['exit_code']==0
 assert batch['status']=='implemented_tested_not_production_integrated'
 assert not any(batch[k] for k in ('clinical_screen_implementation','independently_reviewed','clinically_reviewed','accepted','external_certification'))
 for path in row['source_files']:assert (r/path).is_file(),path
for phase in phases['phases']:
 if phase['id'] in ('P01','P02','P03','P04','P05'):
  assert phase['batches_completed']==sum(x['id'].startswith(phase['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches'])
assert len(screens)==261 and all(x['code_status']=='not_implemented' for x in screens)
assert ledger['release_status']=='NOT_CERTIFIED'
print('PASS v0.12.0: 10 distinct source/unit batches, 261 clinical screens still unimplemented and release gates closed')
