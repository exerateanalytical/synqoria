#!/usr/bin/env python3
"""Derive v0.54 evidence ONLY from executable logs already created in this run."""
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parents[1]
E=R/'evidence/v540'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

batches=[]
for n in range(229,289):
 bid=f'P06-B{n:04d}'; log=E/(bid+'.log')
 if not log.is_file():raise RuntimeError('missing executed scenario '+bid)
 body=log.read_text()
 if n in range(269,279):
  passed=bool(re.search(r'"id":\s*"'+bid+r'"',body) and re.search(r'"pass":\s*true',body))
 else:passed=bool(re.search(r'^'+bid+r' PASS checks=\d+',body,re.M))
 if not passed:raise RuntimeError('scenario has no PASS evidence: '+bid+' '+body[:300])
 test='services/clinical-core/src/test/java/org/synqoria/triage/'+('TriageGenesisBatchesTest.java' if n<=240 else 'TriageHttpBoundaryBatchesTest.java') if n not in range(269,279) else 'tools/triage_genesis_migration_gate.py'
 batches.append({'id':bid,'status':'internally_verified_synthetic_or_static_NOT_certified',
  'test_kind':'static SQL negative mutation/lock' if 269<=n<=278 else ('synthetic atomic transaction' if n<=240 else 'loopback HTTP with synthetic identity and persistence'),
  'test_source':test,'test_log':str(log.relative_to(R)),'test_log_sha256':sha(log),
  'individually_executed':True,'passed':True,'real_postgres_tested':False,'clinical_acceptance':False})

suites=['triage_domain_60','triage_transaction','triage_journey','triage_persistence_contract',
 'triage_49_auth_gateway','triage_10_read_gateway','triage_v015_sql','triage_v016_sql','triage_v017_sql',
 'triage_v018_sql','flyway_inventory','flyway_sha256','ux_core','source_contracts','canonical_registers','release_gate']
results=[]
for name in suites:
 log=E/(name+'.log')
 if not log.exists():raise RuntimeError('missing regression log '+name)
 body=log.read_text()
 if name=='triage_persistence_contract':ok=len(re.findall(r'^.+ exit=0 PASS ',body,re.M))==45
 elif name=='triage_49_auth_gateway':ok=len(re.findall(r'^P06-B\d{4} PASS ',body,re.M))==49
 elif name=='triage_10_read_gateway':ok=len(re.findall(r'^P06-B\d{4} PASS ',body,re.M))==10
 elif name=='triage_domain_60':ok='VERIFIED_BATCHES=60' in body
 elif name=='triage_transaction':ok='TRIAGE_V520_TRANSACTION_TEST_PASS' in body
 elif name=='triage_journey':ok='TRIAGE_SYNTHETIC_JOURNEY_PASS' in body
 elif name in ('triage_v015_sql','triage_v016_sql','triage_v017_sql'):ok='OK' in body and 'FAILED' not in body
 elif name=='triage_v018_sql':ok='"ok": true' in body
 elif name=='flyway_inventory':ok='"count": 18' in body
 elif name=='flyway_sha256':ok='"locked": 18' in body
 elif name=='ux_core':ok='# fail 0' in body and '# pass 109' in body
 elif name=='source_contracts':ok='PASS' in body and 'Traceback' not in body and 'FAIL' not in body
 elif name=='canonical_registers':ok='PASS' in body and 'Traceback' not in body and 'FAIL' not in body
 elif name=='release_gate':ok='PASS: fail-closed release lock' in body
 else:ok=False
 results.append({'name':name,'passed':ok,'log':str(log.relative_to(R)),'sha256':sha(log)})
 if not ok:print('REGRESSION NOT VERIFIED:',name,body[-400:])
report={'checkpoint':'v0.54.0','source_checkpoint_sha256':'e6627ee17b03478c61a2b10df0ecf8569a954406447a34f997e5ccd584fccd36',
 'target':60,'verified_batches':len(batches),'remaining':60-len(batches),
 'completion_percent':round(len(batches)*100/60,2),'remaining_percent':round((60-len(batches))*100/60,2),
 'batch_scope':'60 distinct individually executed security, HTTP, genesis and migration acceptance scenarios, NOT 60 independent clinical modules or released features',
 'regressions_passed':sum(r['passed'] for r in results),'regressions_total':len(results),
 'production_oidc_verified':False,'live_postgresql_verified':False,'clinical_acceptance':False,
 'batches':batches,'regressions':results}
(E/'execution.json').write_text(json.dumps(report,indent=2)+'\n')
if any(not x['passed'] for x in results):sys.exit(3)
print('PASS',len(batches),'individual batches;',sum(r['passed'] for r in results),'/',len(results),'regression groups')
