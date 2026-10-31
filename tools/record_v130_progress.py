"""Record ONLY ten verified source increments and preserve fail-closed canonical statuses."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
def read(path):return json.loads((r/path).read_text())
def write(path,obj):(r/path).write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n')
e=read('evidence/batches/P01-P05-v130-ten-batches.json')
assert e['batches_passed']==10 and len(e['results'])==10 and all(x['verified'] and x['exit_code']==0 for x in e['results'])
ledger=read('registers/batch-ledger.json');phases=read('registers/phases.json');blockers=read('registers/blockers.json')
assert ledger['version']==phases['version']=='0.12.0'
scopes={
'P05-B0006':'Keyed, framed HMAC-SHA256 provisional identity fingerprint bound to tenant and facility; deterministic, cross-scope and tamper unit tests.',
'P05-B0007':'Server-owned cryptographically randomized patient/command/correlation UUID issuance; no client identifiers; unit tested.',
'P05-B0008':'Internal duplicate-review permit predicate with distinct reviewer, registrar, scope, expiry, fingerprint and candidate revision checks; no verified issuer.',
'P05-B0009':'Idempotency replay comparison denies cross-scope/conflicting IDs and unconfirmed outcomes; no durable replay DB proof.',
'P05-B0010':'Bounded reviewer-only parameterized candidate lookup plan and quarantine-only lookup table with FORCE RLS, no grants/policies; not executed in PostgreSQL.',
'P04-B0012':'Trusted transaction-local tenant/facility/actor scope binding using parameterized JDBC set_config; JDBC proxy assertions only.',
'P02-B0030':'Volatile registration draft lifecycle clears PHI on facility/actor/epoch mismatch and refuses client-side duplicate clearance; Node tested.',
'P02-B0031':'EN/FR privacy-safe registration validation feedback and accessible live summary in disabled Next source; Node and source tested.',
'P02-B0032':'Staff-only native registration read-only preview and shared four-tab admission model; Node and source tested, Expo not built.',
'P01-B0029':'OpenAPI 3.1 component-only registration draft with no exposed paths and no caller-controlled authority/IDs; parsed and statically tested.'}
existing={x['id'] for x in ledger['batches']}
for item in e['results']:
 id=item['batch'];assert id not in existing and id in scopes
 ledger['batches'].append({'id':id,'status':'implemented_tested_not_production_integrated','scope':scopes[id],
 'evidence':item['source_files']+['evidence/batches/P01-P05-v130-ten-batches.json'],
 'verification_level':'unit_or_static_only','clinical_screen_implementation':False,'independently_reviewed':False,
 'clinically_reviewed':False,'accepted':False,'external_certification':False})
ledger['version']='0.13.0'
ledger['next_batch']='P05-B0011: verified duplicate-review authority and durable PostgreSQL RLS/role integration; P02-B0033: compiled accessible staff intake after approved auth transport; P01-B0030: reproducible builds and CI dependencies'
phases['version']='0.13.0';phases['checkpoint']='Source-only identity review and safe intake foundations; 10 unit/static increments, NO live clinical registration'
for phase in phases['phases']:
 phase['batches_completed']=sum(b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
 if phase['id'] in ('P01','P02','P04','P05'):
  phase['evidence'].append('evidence/batches/P01-P05-v130-ten-batches.json')
blockers['checkpoint']='v0.13.0'
for item in blockers['blockers']:
 if item['id']=='BLK-012' and item['severity']=='clinical_release':item['id']='BLK-014'
assert len({x['id'] for x in blockers['blockers']})==len(blockers['blockers'])
blockers['blockers'].append({'id':'BLK-015','severity':'clinical_data_integrity','affects':['P04','P05','P19'],
'issue':'v0.13.0 review permit and replay predicates are INTERNAL test seams; no trusted issuer, signed reviewer decision, authoritative duplicate search/index, durable replay claim, restricted-role RLS or real DB execution. Quarantined V006 lookup table has no access policies or grants.',
'mitigation':'Require hospital-approved reviewer workflow, server-owned signed/revision-bound proof, restricted PostgreSQL roles and real cross-tenant/facility adversarial tests before exposing patient registration.',
'status':'open'})
blockers['note']='v0.13.0 10 unit/static batches; PostgreSQL/OIDC/Next/Expo unverified and patient registration save disabled.'
write('registers/batch-ledger.json',ledger);write('registers/phases.json',phases);write('registers/blockers.json',blockers)
assert len(read('registers/canonical-modules.json')['module_register'])==40
assert len(read('registers/canonical-screens.json')['screen_register'])==261
assert all(x['code_status']=='not_implemented' for x in read('registers/canonical-screens.json')['screen_register'])
print('PASS: 10 new ledger entries, phase counters recalculated, all 261 screens remain not_implemented')
