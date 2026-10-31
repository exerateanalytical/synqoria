"""Reconcile actual historical batch evidence and this run; never promote clinical acceptance."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
ledger_path=root/'registers/batch-ledger.json';ledger=json.loads(ledger_path.read_text())
known={x['id'] for x in ledger['batches']}
for version in range(320,401,10):
 p=root/f'evidence/batches/v{version}-executed.json'
 if not p.exists():continue
 data=json.loads(p.read_text());rows=data if isinstance(data,list) else [data]
 for r in rows:
  batch=r.get('batch')
  if not batch or batch in known:continue
  exit_code=r.get('test_exit',r.get('test_exit_code',r.get('exit_code')))
  if exit_code is None and r.get('compiled') and r.get('test_exit_code')==0:exit_code=0
  verified=(exit_code==0 and (r.get('compile_exit',0)==0) and r.get('verified',True))
  if version==390:
   verified=('javac exit 0; java exit 0' in r.get('test',''))
  if not verified:continue
  ledger['batches'].append({'id':batch,'status':'historical_source_or_mock_test_evidence_recovered_not_live_integration',
   'scope':str(r.get('component',r.get('implementation',r.get('scope',r.get('test','Historical source evidence'))))),
   'evidence':[p.relative_to(root).as_posix()],
   'specified':True,'designed':True,'implemented':True,'tested':True,'integration_tested':False,
   'independently_reviewed':False,'clinically_reviewed':False,'accepted':False,'external_certification':False})
  known.add(batch)
current=root/'evidence/batches/v410-executed.json';run=json.loads(current.read_text())
assert run['completed_batches']==20 and run['remaining_batches']==0
for r in run['results']:
 assert r['verified'] and r['exit_code']==0
 batch=r['batch']
 if batch in known:continue
 ledger['batches'].append({'id':batch,
  'status':'static_migration_verified_NOT_executed' if r['test_type']=='source_static_only' else 'implemented_java_unit_or_mock_jdbc_verified_NOT_live_postgres',
  'scope':r['scope'],'evidence':['evidence/batches/v410-executed.json'],
  'specified':True,'designed':True,'implemented':True,'tested':True,'integration_tested':False,
  'independently_reviewed':False,'clinically_reviewed':False,'accepted':False,'external_certification':False})
 known.add(batch)
assert len(known)==len(ledger['batches'])
for n in range(1,64):assert f'P06-B{n:04d}' in known,n
ledger['version']='0.41.0';ledger['next_batch']='P06-B0064';ledger['release_status']='NOT_CERTIFIED'
ledger_path.write_text(json.dumps(ledger,indent=2)+'\n')
phases_path=root/'registers/phases.json';phases=json.loads(phases_path.read_text())
phases['version']='0.41.0';phases['checkpoint']='v0.41.0'
for phase in phases['phases']:
 phase['batches_completed']=sum(b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
 if phase['id']=='P06':
  phase['status']='quarantined_transaction_adapter_mock_tested_not_integrated'
  phase['evidence']=list(dict.fromkeys(phase.get('evidence',[])+['evidence/batches/v410-executed.json']))
 assert phase['certification'] in ('not_certified','source_integrity_verified_only')
phases_path.write_text(json.dumps(phases,indent=2)+'\n')
for name in ('module-status-matrix.json','screen-status-matrix.json'):
 p=root/'registers'/name;j=json.loads(p.read_text());j['version']='0.41.0'
 for row in j['items']:
  assert not row['accepted'] and not row['clinically_reviewed']
  if row['id']=='M02':row['component_progress_note']='P06-B0044..B0063: quarantined scheduling JDBC adapter and domain safety tests; module NOT integrated, clinically reviewed or accepted.'
 p.write_text(json.dumps(j,indent=2)+'\n')
blockers_path=root/'registers/blockers.json';blockers=json.loads(blockers_path.read_text());blockers['checkpoint']='v0.41.0'
if not any(x['id']=='BLK-V410-M02-DB' for x in blockers['blockers']):blockers['blockers'].append({'id':'BLK-V410-M02-DB','severity':'clinical_release','affects':['P06','M02','P19'],
 'issue':'V012-V014 quarantined appointment migrations not executed on live PostgreSQL. No reviewed RLS policies, non-owner restricted DB role, real concurrent double-booking/rollback, recovery, outbox delivery or database-backed idempotency tests.',
 'mitigation':'Provision isolated synthetic PostgreSQL test cell; independent DBA/security review of schema, RLS and grants; execute restricted-role integration and concurrency suites before any application endpoint activation.',
 'status':'open'})
if not any(x['id']=='BLK-V410-M02-CLINICAL' for x in blockers['blockers']):blockers['blockers'].append({'id':'BLK-V410-M02-CLINICAL','severity':'clinical_release','affects':['P06','M02','M03','P19'],
 'issue':'No signed hospital clinical safety/operational review, no 261-screen acceptance or certified 40-module inventory. Triage/medication/clinical writes remain disabled.',
 'mitigation':'Obtain qualified hospital clinical and security sign-offs; review patient consent, triage protocols, workflow hazards and operational downtime/recovery before pilot release.',
 'status':'open'})
blockers_path.write_text(json.dumps(blockers,indent=2)+'\n')
print('PASS ledger reconciled historical P06-B0020..B0043 plus 20 current batches; P06 count=63, no clinical acceptance')
