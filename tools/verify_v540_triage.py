#!/usr/bin/env python3
"""Execute 60 distinct triage security acceptance scenarios and independent cumulative regressions.
Every scenario has an isolated subprocess result; no clinical or live database certification implied.
"""
from pathlib import Path
import subprocess,json,sys,time
ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'evidence/v540';EVIDENCE.mkdir(parents=True,exist_ok=True)
CLASSES=Path('/mnt/data/synqoria_v54_classes')
MAIN=ROOT/'services/clinical-core/src/main/java'
TEST=ROOT/'services/clinical-core/src/test/java'
DB=ROOT/'services/clinical-core/db/migration'
results=[]

def run(label,argv,timeout=120):
 start=time.monotonic()
 try:
  r=subprocess.run([str(x) for x in argv],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=timeout)
  record={'name':label,'passed':r.returncode==0,'exit_code':r.returncode,'elapsed_seconds':round(time.monotonic()-start,3),'argv':[str(x) for x in argv]}
  output=r.stdout+'\n--- STDERR ---\n'+r.stderr
 except subprocess.TimeoutExpired as ex:
  record={'name':label,'passed':False,'exit_code':None,'timeout':timeout,'elapsed_seconds':round(time.monotonic()-start,3),'argv':[str(x) for x in argv]}
  output='TIMEOUT '+str(ex)
 (EVIDENCE/(label+'.log')).write_text(output,encoding='utf8')
 record['log']='evidence/v540/'+label+'.log'
 results.append(record)
 return record

main=sorted(MAIN.rglob('*.java'));test=sorted(TEST.rglob('*.java'))
CLASSES.mkdir(parents=True,exist_ok=True)
compile=run('javac_full',['javac','-encoding','UTF-8','-d',str(CLASSES),*main,*test],180)
if not compile['passed']:
 (EVIDENCE/'execution.json').write_text(json.dumps({'compile':compile,'verified_batches':0,'results':results},indent=2)+'\n')
 sys.exit(1)

for n in range(229,289):
 id=f'P06-B{n:04d}'
 if n<=240:cmd=['java','-cp',str(CLASSES),'org.synqoria.triage.TriageGenesisBatchesTest',id]
 elif n<=268 or n>=279:cmd=['java','-cp',str(CLASSES),'org.synqoria.triage.TriageHttpBoundaryBatchesTest',id]
 else:cmd=['python','tools/triage_genesis_migration_gate.py','--batch',id]
 r=run(id,cmd,25)
 log=(EVIDENCE/(id+'.log')).read_text()
 if not r['passed'] or id not in log or (' PASS' not in log and '"pass": true' not in log):
  r['passed']=False
  print('FAIL',id,flush=True)
 elif n%10==8:print('VERIFIED',n-228,'of 60',flush=True)

suites={
 'triage_domain_60':['java','-cp',str(CLASSES),'org.synqoria.triage.TriageWorkflowBatchesTest'],
 'triage_transaction':['java','-cp',str(CLASSES),'org.synqoria.triage.TriagePersistenceIntegrationTest'],
 'triage_journey':['java','-cp',str(CLASSES),'org.synqoria.triage.TriageJourneyIntegrationTest'],
 'triage_49_auth_gateway':['java','-cp',str(CLASSES),'org.synqoria.triage.TriageAuthenticatedFeatureBatchesTest','P06-B0169'],
 'triage_10_read_gateway':['java','-cp',str(CLASSES),'org.synqoria.triage.TriageAuthenticatedReadBatchesTest','P06-B0218'],
 'triage_v015_sql':['python','-m','unittest','discover','-s','tests','-p','test_triage_migration_gate.py','-v'],
 'triage_v016_sql':['python','-m','unittest','discover','-s','tests','-p','test_triage_encrypted_migration_gate.py','-v'],
 'triage_v017_sql':['python','-m','unittest','discover','-s','tests','-p','test_triage_secure_migration_v520.py','-v'],
 'triage_v018_sql':['python','tools/triage_genesis_migration_gate.py'],
 'flyway_inventory':['python','tools/migration_inventory_gate.py',DB],
 'flyway_sha256':['python','tools/migration_checksum_gate.py',DB,'registers/migration-sha256-lock.json'],
 'ux_core':['npm','run','check:ux'],
 'source_contracts':['npm','run','check:source'],
 'canonical_registers':['npm','run','check:registers'],
 'release_gate':['python','tools/verify_release_gate.py'],
}
for name,cmd in suites.items():
 r=run(name,cmd,120)
 print('REGRESSION',name,'PASS' if r['passed'] else 'FAIL',flush=True)
report={'checkpoint':'v0.54.0','source_base':'v0.53.0 SHA-256 e6627ee17b03478c61a2b10df0ecf8569a954406447a34f997e5ccd584fccd36',
 'target':60,'tested_batches':60,'verified_batches':sum(r['passed'] for r in results if r['name'].startswith('P06-B')),
 'failed_batch_ids':[r['name'] for r in results if r['name'].startswith('P06-B') and not r['passed']],
 'regression_passed':sum(r['passed'] for r in results if r['name'] in suites),'regression_total':len(suites),
 'java_main_sources':len(main),'java_test_sources':len(test),'live_postgres_tested':False,
 'production_oidc_tested':False,'clinical_acceptance':False,
 'interpretation':'These are individually executable security/transaction/migration acceptance scenarios; not 60 new modules or production-certified workflows.',
 'results':results}
(EVIDENCE/'execution.json').write_text(json.dumps(report,indent=2)+'\n')
print('FINAL',report['verified_batches'],'/60','regressions',report['regression_passed'],'/',report['regression_total'],flush=True)
sys.exit(0 if report['verified_batches']==60 and report['regression_passed']==len(suites) else 2)
