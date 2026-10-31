"""Advance only verified source/unit batches; never certify clinical release."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
load=lambda p:json.loads((root/p).read_text())
def save(p,v):(root/p).write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n')
ledger=load('registers/batch-ledger.json')
assert ledger['version']=='0.22.0'
new=[
('P05-B0043','V2 scope-bound review digest includes tenant, facility, review and command IDs to prevent event transplant','java_unit_only'),
('P05-B0044','Independent V2 event-history verifier enforces scope, digest chain, unique commands, bounded length and legal transitions','java_unit_only'),
('P05-B0045','Minimal non-PHI receipt derived only from verified V2 event history','java_unit_only'),
('P05-B0046','Current approval state preflight rejects revocation, stale reviewer grant epoch and stale versions; never authorizes registration','java_unit_only'),
('P05-B0047','Allowlisted immutable evidence export excludes clinical payload and identifiers','java_unit_only'),
('P02-B0073','Canonical native deep-link gate denies encoded traversal, unauthorized roots and locked sessions','typescript_node_unit_native_source'),
('P02-B0074','Native staff scope switch clears volatile drafts and invalidates previous epoch','typescript_node_unit_native_source'),
('P02-B0075','Abort and discard late native async responses after background/scope transition','typescript_node_unit_native_source'),
('P02-B0076','Declarative non-clipping, scalable EN/FR critical clinical field typography contract','typescript_node_unit_not_device_verified'),
('P02-B0077','Native offline clinical write queue rejects all writes and replay, including when online','typescript_node_unit_native_source')]
assert len(new)==10
for batch,scope,level in new:
 log=root/f'evidence/batches/v230-{batch}.log'
 assert log.is_file() and log.stat().st_size>0
 content=log.read_text()
 assert (f'PASS {batch}' in content) if batch.startswith('P05') else ('# pass 1' in content and '# fail 0' in content)
 ledger['batches'].append(dict(id=batch,status='implemented_unit_or_static_tested_NOT_production_integrated',scope=scope,
  evidence=[f'evidence/batches/v230-{batch}.log'],verification_level=level,clinical_screen_implementation=False,
  independently_reviewed=False,clinically_reviewed=False,accepted=False,external_certification=False))
ledger['version']='0.23.0'
ledger['next_batch']='P05-B0027 BLOCKED: real PostgreSQL reviewer event transactions, RLS, restricted roles and durable outbox. Next independently unblocked P05-B0048 (V2 SQL migration design with DBA review) or P02-B0078 (device-level accessibility integration).'
ledger['release_status']='NOT_CERTIFIED'
save('registers/batch-ledger.json',ledger)
phases=load('registers/phases.json');phases['version']='0.23.0';phases['checkpoint']='v0.23.0: scoped V2 reviewer evidence and native security/navigation boundaries; clinical release locked'
for phase in phases['phases']:
 phase['batches_completed']=sum(b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
 if phase['id'] in {'P02','P05'}:
  ev=phase.setdefault('evidence',[])
  if 'evidence/batches/P02-P05-v230-ten-batches.json' not in ev:ev.append('evidence/batches/P02-P05-v230-ten-batches.json')
 assert phase['certification']!='certified'
save('registers/phases.json',phases)
for file in ('screen-status-matrix.json','module-status-matrix.json'):
 v=load('registers/'+file);v['version']='0.23.0'
 assert all(not row['implemented'] and not row['accepted'] for row in v['items'])
 save('registers/'+file,v)
blockers=load('registers/blockers.json');blockers['checkpoint']='v0.23.0'
blockers['blockers'].append({'id':'BLK-V230-001','severity':'clinical_data_integrity','affects':['P05','P19'],
 'issue':'V2 review digest and event verifier are local Java domain components; V1 event rows cannot be silently interpreted as V2, no migration/backfill/DB enforcement or external custody.',
 'mitigation':'Version-pin V2 SQL and event envelope; DBA/security-reviewed migration, restricted-role RLS and tamper/failure-injection integration tests before wiring any approval writer.',
 'status':'open'})
save('registers/blockers.json',blockers)
for p in [root/'package.json',*root.glob('apps/*/package.json'),root/'packages/ux-core/package.json']:
 v=json.loads(p.read_text());v['version']='0.23.0'
 if '@synqoria/ux-core' in v.get('dependencies',{}):v['dependencies']['@synqoria/ux-core']='0.23.0'
 p.write_text(json.dumps(v,indent=2)+'\n')
results=[]
for batch,scope,level in new:
 results.append(dict(batch=batch,scope=scope,verified=True,exit_code=0,evidence_log=f'evidence/batches/v230-{batch}.log',verification_level=level))
save('evidence/batches/P02-P05-v230-ten-batches.json',dict(checkpoint='v0.23.0',source_checkpoint='v0.22.0',batch_target=10,batches_passed=10,
 results=results,scope_limits=['No PostgreSQL execution or production IAM','No compiled Next.js/Expo builds or device tests','No independent security/clinical signoff','No clinical screen accepted']))
print('PASS v0.23.0 registers: 10 new batches, phase counts reconciled, release locked')
