#!/usr/bin/env python3
"""Update truthful v0.28.0 registers from executed evidence; preserve canonical IDs."""
import json,pathlib,datetime,hashlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
NOW=datetime.datetime.now(datetime.timezone.utc).isoformat()
def load(p):return json.loads((ROOT/p).read_text())
def save(p,x):(ROOT/p).write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n')
e=load('evidence/batches/P01-P02-P06-v280-ten-batches.json')
assert e['batches_passed']==10 and len(e['results'])==10 and all(x['verified'] and x['exit_code']==0 for x in e['results'])
for p in ['package.json','apps/web/package.json','apps/staff-mobile/package.json','apps/patient-mobile/package.json','packages/ux-core/package.json']:
 x=load(p);assert x['version']=='0.27.0';x['version']='0.28.0'
 for k in ('dependencies','devDependencies'):
  for name,version in x.get(k,{}).items():
   if name.startswith('@synqoria/'):
    assert version=='0.27.0';x[k][name]='0.28.0'
 save(p,x)
ledger=load('registers/batch-ledger.json');existing={x['id'] for x in ledger['batches']}
for b in e['results']:
 assert b['batch'] not in existing
 ledger['batches'].append({'id':b['batch'],'status':'implemented_and_unit_or_static_verified_NOT_production_integrated',
 'scope':b['scope'],'evidence':['evidence/batches/P01-P02-P06-v280-ten-batches.json',b['log']],
 'external_certification':False,'specified':True,'designed':True,'implemented':True,'tested':True,
 'integration_tested':False,'independently_reviewed':False,'clinically_reviewed':False,'accepted':False})
ledger['version']='0.28.0'
ledger['next_batch']='P01-B0045 (network-enabled CI candidate lock review and actual npm ci/Next/Expo builds). P05-B0027 remains blocked pending live PostgreSQL restricted-role/RLS. P06-B0007: authoritative appointment transaction port, pending approved DB constraints; safe source work only.'
ledger['release_status']='NOT_CERTIFIED'
save('registers/batch-ledger.json',ledger)
phases=load('registers/phases.json');phases['version']='0.28.0';phases['checkpoint']='v0.28.0'
for p in phases['phases']:
 if p['id'] in ('P01','P02','P06'):
  p['batches_completed']=sum(x['id'].startswith(p['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches'])
  p['certification']='not_certified'
  p.setdefault('evidence',[]).append('evidence/batches/P01-P02-P06-v280-ten-batches.json')
  if p['id']=='P06':p['status']='foundations_only_pending_P05_and_live_postgres'
save('registers/phases.json',phases)
for p in ['registers/module-status-matrix.json','registers/screen-status-matrix.json']:
 x=load(p);x['version']='0.28.0';save(p,x)
blockers=load('registers/blockers.json');blockers['checkpoint']='v0.28.0';blockers['last_reviewed_utc']=NOW
blockers['blockers'] += [
 {'id':'BLK-V280-BUILDS','severity':'engineering','affects':['P01-B0045','P02-B0095','P19'],
  'issue':'npm registry DNS EAI_AGAIN reconfirmed; no reviewed package-lock.json, no installed Next.js/Expo dependencies, no compiled builds. Manual CI candidate workflow is source-only and has not run.',
  'mitigation':'Run manual dependency-lock-candidate workflow in trusted network-enabled CI; review lock/integrity/peer compatibility; merge reviewed lock; npm ci, Next build and both Expo exports with artifact attestation.','status':'open'},
 {'id':'BLK-V280-P06-DATABASE','severity':'security_and_clinical','affects':['P06','M02','M02-S01','M02-S02','M02-S03','M02-S04','M02-S05'],
  'issue':'Scheduling primitives tested only in memory. PostgreSQL exclusion constraint and FORCE RLS are draft quarantine SQL; no live PostgreSQL/restricted role/approved patient-scope policy/serializable outbox tests.',
  'mitigation':'DBA/security review migration, run real PostgreSQL concurrency, row-level isolation, command replay and atomic audit/outbox integration tests. No booking writes before gates.','status':'open'},
 {'id':'BLK-V280-UI-DEVICES','severity':'engineering','affects':['P02','P19'],
  'issue':'Mobile More focus trap executed in Chromium DOM and CSS in static specimen, not compiled Next.js or native Expo devices. No screen-reader/keyboard/200% font physical-device certification.',
  'mitigation':'Build apps with reviewed lock and test EN/FR navigation at all widths, physical Android/iOS, assistive technologies and verified locally hosted Inter.','status':'open'}]
save('registers/blockers.json',blockers)
# Preserve canonical inventory exactly; no clinical screen/module is promoted by source-only foundation.
for p,total in [('registers/module-status-matrix.json',40),('registers/screen-status-matrix.json',261)]:
 x=load(p);assert len(x['items'])==total
 assert all(not row['implemented'] and not row['accepted'] and not row['clinically_reviewed'] for row in x['items'])
print('PASS v0.28 registers: 10 appended, 40 modules, 261 screens; no phase or clinical certification')
