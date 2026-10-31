"""Reconcile verified v0.25.0 evidence into canonical registers; never certify clinical scope."""
from pathlib import Path
import json,datetime
ROOT=Path(__file__).resolve().parents[1]
read=lambda p:json.loads((ROOT/p).read_text())
write=lambda p,d:(ROOT/p).write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n')
report=read('evidence/batches/P01-P02-P05-v250-ten-batches.json')
assert report['batch_target']==10 and report['batches_passed']==10 and len(report['results'])==10
assert all(x['verified'] and x['exit_code']==0 and (ROOT/x['evidence_log']).exists() for x in report['results'])
ledger=read('registers/batch-ledger.json')
assert ledger['version']=='0.24.0'
existing={x['id'] for x in ledger['batches']}
for bid,scope in [
 ('P02-B0083','Next.js compiled application gate blocked by missing npm dependencies and registry DNS'),
 ('P05-B0027','Live PostgreSQL restricted-role/RLS reviewer transactions and independent clinical approval blocked')]:
 if bid not in existing:
  ledger['batches'].append({'id':bid,'status':'blocked_attempt_not_completed','scope':scope,
   'evidence':['registers/blockers.json'],'verification_level':'not_tested','clinical_screen_implementation':False,
   'integration_tested':False,'independently_reviewed':False,'clinically_reviewed':False,'accepted':False,'external_certification':False})
scopes={
 'P01-B0033':'Executable offline SQL migration dependency, RLS FORCE and PUBLIC privilege audit with mutation-negative tests; no PostgreSQL execution.',
 'P01-B0034':'Read-only PostgreSQL restricted-role readiness probe with sanitized diagnostics and mocked psql contract; actual DB unavailable.',
 'P01-B0035':'Offline Next.js/Expo build prerequisite auditor detects missing lockfile, dependencies and version skew; real builds blocked.',
 'P02-B0084':'Next.js source More control with keyboard Escape/focus restoration, bilingual options and no unauthorized links; static Chromium behavior verified.',
 'P02-B0085':'Static Chromium responsive 9-width, French/200% reflow and More panel screenshots, not a compiled Next.js application.',
 'P05-B0053':'Unapplied V010 composite-key reviewer audit/outbox foreign keys, FORCE RLS and PUBLIC revoke; DDL static checks.',
 'P05-B0054':'JDBC restricted-role PostgreSQL metadata and FORCE RLS attestation, mocked JDBC only.',
 'P05-B0055':'Serializable scoped reviewer decision row lock, expiry/revision checks and bounded V2 history read, mocked JDBC.',
 'P05-B0056':'Internal same-transaction reviewer event/outbox/audit insert with affected-row and rollback checks, mocked JDBC.',
 'P05-B0057':'Internal restricted reviewer transaction orchestration with role check, lock, state/digest validation and rollback, mocked JDBC; not wired to production.'}
for x in report['results']:
 bid=x['batch'];assert bid not in existing
 ledger['batches'].append({'id':bid,'status':'implemented_unit_or_static_tested_NOT_production_integrated','scope':scopes[bid],
  'evidence':[x['evidence_log'],'evidence/batches/P01-P02-P05-v250-ten-batches.json'],
  'verification_level':'mock_jdbc_unit' if bid.startswith('P05') else 'chromium_static_specimen' if bid.startswith('P02') else 'python_unit_and_diagnostic',
  'clinical_screen_implementation':False,'integration_tested':False,'independently_reviewed':False,'clinically_reviewed':False,'accepted':False,'external_certification':False})
ledger['version']='0.25.0';ledger['next_batch']='P05-B0027 BLOCKED live PostgreSQL + restricted role/RLS. Next unblocked: P01-B0036 application build reproducibility or P02-B0086 accessible mobile app validation.'
ledger['release_status']='NOT_CERTIFIED'
ledger['merge_provenance']={'source':'SYNQORIA_v0.24.0_cumulative.zip','source_manifest_verified':True,'batch_evidence':'evidence/batches/P01-P02-P05-v250-ten-batches.json','clinical_release':'DENIED'}
write('registers/batch-ledger.json',ledger)
phases=read('registers/phases.json');phases['version']='0.25.0';phases['checkpoint']='v0.25.0'
for p in phases['phases']:
 p['batches_completed']=sum(x['id'].startswith(p['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches'])
 if p['id'] in ('P01','P02','P05'):p['evidence'].append('evidence/batches/P01-P02-P05-v250-ten-batches.json')
 if p['id']!='P00':p['certification']='not_certified'
write('registers/phases.json',phases)
for f in ('registers/module-status-matrix.json','registers/screen-status-matrix.json'):
 d=read(f);d['version']='0.25.0'
 assert all(not x['implemented'] and not x['accepted'] for x in d['items'])
 write(f,d)
blockers=read('registers/blockers.json');blockers['checkpoint']='v0.25.0'
blockers['blockers'].extend([
 {'id':'BLK-V250-POSTGRES','severity':'infrastructure','affects':['P01','P03','P05','P19'],
  'issue':'v0.25.0 reviewer transaction adapter passed mock JDBC but no PostgreSQL executable/driver/DSN, no restricted role or actual FORCE-RLS policy grants; V010 migration UNAPPLIED.',
  'mitigation':'Provision PostgreSQL >=16, reviewed restricted role and RLS policies; execute migrations, negative cross-tenant tests, transaction rollback and concurrency tests with synthetic data. Clinical release remains denied.','status':'open'},
 {'id':'BLK-V250-BUILDS','severity':'engineering','affects':['P01','P02','P13','P19'],
  'issue':'npm registry EAI_AGAIN, missing root lockfile and Next/Expo installed dependencies; dependency preflight returned blocked; no compiled web/mobile binaries.',
  'mitigation':'Run pinned npm lock/install and actual Next/Expo builds in network-enabled CI; capture build logs and device tests.','status':'open'},
 {'id':'BLK-V250-LIBRARY','severity':'persistence','affects':['P00','P01'],
  'issue':'Earlier v0.22–v0.24 uploads were rejected by Library storage quota; v0.25 upload must be independently verified.',
  'mitigation':'Keep complete ZIP and checksum in conversation; recover quota or authorized external GitHub/Drive backup; never claim Library saved unless upload confirms.','status':'open'}])
write('registers/blockers.json',blockers)
print('PASS 10 individually evidenced batches, canonical phase counts:',[(p['id'],p['batches_completed']) for p in phases['phases'][:6]])
