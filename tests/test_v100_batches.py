from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
ledger=json.loads((root/'registers/batch-ledger.json').read_text()); records=json.loads((root/'evidence/batches/P01-P04-v010-ten-batches.json').read_text())
assert tuple(map(int,ledger['version'].split('.'))) >= (0,10,0) and ledger['release_status']=='NOT_CERTIFIED'
assert records['batch_target']==records['batches_passed']==len(records['results'])==10
assert len(set(x['batch'] for x in records['results']))==10
for row in records['results']:
 assert row['verified'] and row['exit_code']==0 and row['not_clinically_certified']
 item=next(x for x in ledger['batches'] if x['id']==row['batch'])
 assert item['status']=='implemented_tested_not_production_integrated'
 assert not any(item[k] for k in ['clinical_screen_implementation','accepted','clinically_reviewed','external_certification'])
 for f in row['source_files']:assert (root/f).is_file(),f
 for f in item['evidence']:assert (root/f).is_file(),(row['batch'],f)
phases=json.loads((root/'registers/phases.json').read_text())['phases']
for x in phases:
 if x['id'] in ('P01','P02','P03','P04'):
  actual=sum(b['id'].startswith(x['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
  assert actual==x['batches_completed'] and x['certification']=='not_certified',(x['id'],actual)
print('PASS v0.10.0: 10 batch files and fail-closed phase/certification counters')
