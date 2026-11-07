#!/usr/bin/env python3
"""Reconcile source-version registers and preserve all clinical release denials."""
from pathlib import Path
from datetime import datetime,timezone
import json
root=Path(__file__).resolve().parent.parent
load=lambda rel:json.loads((root/rel).read_text())
write=lambda rel,v:(root/rel).write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n')
report=load('evidence/v610/verified-batches.json')
assert report['completed']==10 and all(x['passed'] and x['exit_code']==0 for x in report['batches'])
ledger=load('registers/batch-ledger.json');assert ledger['version']=='0.60.0'
existing={x['id'] for x in ledger['batches']};assert not existing.intersection({r['batch'] for r in report['batches']})
for item in report['batches']:
 row={'id':item['batch'],'module':('M04' if item['batch'] in {'P07-B0062','P07-B0065','P07-B0066','P07-B0067','P07-B0068'} else
       'M05' if item['batch']=='P07-B0063' else 'M06' if item['batch']=='P07-B0064' else 'M04/M05/M06'),
      'status':'implemented_and_individually_tested_HTTP_or_contract_NOT_production_qualified',
      'scope':item['implemented_area'],
      'evidence':[item['log'],'evidence/v610/verified-batches.json'],
      'integration_tested':'synthetic_loopback_or_contract_only',
      'database_integration_qualified':False,'independent_clinical_review':False,'external_certification':False,
      'clinical_screen_accepted':False}
 ledger['batches'].append(row)
ledger['version']='0.61.0';ledger['next_parallel_batch']='P07-B0071';ledger['release_status']='NOT_CERTIFIED'
ledger['concurrency_note']='P07-B0061–B0070 on stacked draft PR4 only. P06-B0392 remains reserved for independent triage work. Do not overwrite main.'
write('registers/batch-ledger.json',ledger)
phases=load('registers/phases.json');assert phases['version']=='0.60.0'
phases['version']='0.61.0';phases['checkpoint']='v0.61.0_unmerged_PR4'
p07=next(x for x in phases['phases'] if x['id']=='P07');p07['batches_completed']+=10
p07['status']='domain_and_authenticated_loopback_HTTP_contract_client_implemented_tested_NOT_live_DB_or_OIDC'
p07['evidence'].append('evidence/v610/verified-batches.json')
write('registers/phases.json',phases)
for path in ['registers/module-status-matrix.json','registers/screen-status-matrix.json']:
 d=load(path);d['version']='0.61.0';d['checkpoint']='v0.61.0_unmerged_PR4'
 assert all(not x['accepted'] for x in d['items'])
 write(path,d)
for path in ['package.json','packages/ux-core/package.json','apps/web/package.json',
             'apps/staff-mobile/package.json','apps/patient-mobile/package.json']:
 d=load(path);d['version']='0.61.0';
 if path.startswith('apps/'):
  assert d['dependencies']['@synqoria/ux-core']=='0.60.0'
  d['dependencies']['@synqoria/ux-core']='0.61.0'
 write(path,d)
blockers={'checkpoint':'v0.61.0_unmerged_PR4','updated_at_utc':datetime.now(timezone.utc).isoformat(),
 'certification':'DEFERRED_DO_NOT_MARK_AS_PASSED','clinical_release':'DENIED','blockers':[
  {'id':'P07-LIVE-DB','affected':'M04-M06 clinical writes','detail':'No PostgreSQL row locks/RLS/transactional audit+outbox adapter tested; synthetic store lives only in test tree'},
  {'id':'P07-IDENTITY','affected':'M04-M06 HTTP','detail':'Synthetic trusted session in tests; real OIDC, MFA, revocation and signed evidence unverified'},
  {'id':'P07-APPLICATION','affected':'Java HTTP and TypeScript API client','detail':'Opt-in Java HTTP handler exists without reviewed Spring production wiring; no actual Next.js or Expo device build'},
  {'id':'P07-CLINICAL','affected':'human release gates','detail':'0/261 screens accepted, 0/40 modules certified; clinical review is deferred but not waived'}]}
write('registers/v610-blockers.json',blockers)
report['next_P07']='P07-B0071';report['next_P06']='P06-B0392'
write('registers/v610-implementation-batches.json',report)
# Update run evidence with notes about old failed iterations retained for audit.
print('CHECKPOINT v0.61.0 registered: 10 new batches, P07 count '+str(p07['batches_completed']))
