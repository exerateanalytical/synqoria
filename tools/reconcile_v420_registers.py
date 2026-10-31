"""Advance reconciled v0.41 checkpoint to v0.42 without double-counting historical B0044."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
ledger_path=root/'registers/batch-ledger.json';ledger=json.loads(ledger_path.read_text())
run=json.loads((root/'evidence/batches/v420-executed.json').read_text())
assert run['completed_batches']==20 and run['remaining_batches']==0
known={x['id'] for x in ledger['batches']}
for r in run['results']:
 assert r['verified'] and r['exit_code']==0
 batch=r['batch']
 if batch not in known:
  ledger['batches'].append({'id':batch,
   'status':'static_migration_verified_NOT_executed' if r['test_type']=='source_static_only' else 'implemented_java_unit_or_mock_jdbc_verified_NOT_live_postgres',
   'scope':r['scope'],'evidence':['evidence/batches/v420-executed.json'],
   'specified':True,'designed':True,'implemented':True,'tested':True,'integration_tested':False,
   'independently_reviewed':False,'clinically_reviewed':False,'accepted':False,'external_certification':False})
  known.add(batch)
 else:
  row=next(x for x in ledger['batches'] if x['id']==batch)
  row['evidence']=list(dict.fromkeys(row.get('evidence',[])+['evidence/batches/v420-executed.json']))
b44=next(x for x in ledger['batches'] if x['id']=='P06-B0044')
b44['status']='reconciled_historical_and_reverified_quarantine_SQL_alignment'
b44['scope']='Historical v0.41 transition binder preserved and strengthened to current-state/revision/tenant/facility/patient CAS; 18 Java assertions in v410.'
b44['evidence']=list(dict.fromkeys(b44.get('evidence',[])+['evidence/batches/v410-concurrent-historical.json','evidence/batches/v410-executed.json']))
assert len(known)==len(ledger['batches'])
for n in range(1,65):assert f'P06-B{n:04d}' in known,n
ledger['version']='0.42.0';ledger['next_batch']='P06-B0065';ledger['release_status']='NOT_CERTIFIED'
ledger_path.write_text(json.dumps(ledger,indent=2)+'\n')
p=root/'registers/phases.json';j=json.loads(p.read_text());j['version']='0.42.0';j['checkpoint']='v0.42.0'
for phase in j['phases']:
 phase['batches_completed']=sum(b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
 if phase['id']=='P06':
  phase['status']='quarantined_transaction_adapter_mock_tested_not_integrated'
  phase['evidence']=list(dict.fromkeys(phase.get('evidence',[])+['evidence/batches/v420-executed.json']))
 assert phase['certification'] in ('not_certified','source_integrity_verified_only')
p.write_text(json.dumps(j,indent=2)+'\n')
for name in ('module-status-matrix.json','screen-status-matrix.json'):
 p=root/'registers'/name;j=json.loads(p.read_text());j['version']='0.42.0'
 for row in j['items']:
  assert not row['accepted'] and not row['clinically_reviewed']
  if row['id']=='M02':row['component_progress_note']='P06-B0044..B0064: quarantined scheduling JDBC adapter, replay proof and domain tests; module NOT live-integrated, clinically reviewed or accepted.'
 p.write_text(json.dumps(j,indent=2)+'\n')
p=root/'registers/blockers.json';j=json.loads(p.read_text());j['checkpoint']='v0.42.0';p.write_text(json.dumps(j,indent=2)+'\n')
print('PASS v0.42 reconciled: historical B0044 retained, 20 new B0045..B0064 verified, P06=64 foundational IDs, no clinical acceptance')
