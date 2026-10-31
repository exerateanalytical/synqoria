from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
ledger=json.loads((root/'registers/batch-ledger.json').read_text())
assert tuple(map(int,ledger['version'].split('.'))) >= (0,6,0) and ledger['release_status']=='NOT_CERTIFIED'
assert len([b for b in ledger['batches'] if b['status'].startswith('blocked')])>=1
ids=[b['id'] for b in ledger['batches']];assert len(ids)==len(set(ids))
m=json.loads((root/'evidence/batches/P01-P04-v060-merge-record.json').read_text())
assert len(m['merged_unique_batches'])==9 and m['total_distinct_new_since_v0_4']==19
for bid in m['merged_unique_batches']:
 b=next(b for b in ledger['batches'] if b['id']==bid)
 assert not b['external_certification'] and not b['accepted'] and not b['integration_tested']
 for p in b['evidence']:
  assert (root/p).is_file(),(bid,p)
p=json.loads((root/'registers/phases.json').read_text())['phases']
for phase in p:
 if phase['id'] in ('P01','P02','P03','P04'):
  assert phase['batches_completed']==len([b for b in ledger['batches'] if b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked')])
  assert phase['certification']=='not_certified'
assert sum(phase['batches_completed'] for phase in p[:5])>=30  # historical 30 remain present; later batches append
assert all(x['code_status']=='not_implemented' for x in json.loads((root/'registers/canonical-screens.json').read_text())['screen_register'])
print('PASS: 19 reconciled historical increments retained, 30 historical batches incl P00, no clinical screens or certifications')
