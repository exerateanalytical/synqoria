#!/usr/bin/env python3
"""Run the 60 distinct M03 clinical-workflow feature checks with individual process evidence.
This is an internal source/domain verification gate, never a clinical acceptance certificate.
"""
from pathlib import Path
import subprocess, json, hashlib, zipfile, os, re, time, sys
ROOT=Path(__file__).resolve().parents[1]
EV=ROOT/'evidence/v510';EV.mkdir(parents=True,exist_ok=True)
CLASSES=EV/'classes';CLASSES.mkdir(exist_ok=True)
SRC=ROOT/'services/clinical-core/src';MAIN=SRC/'main/java';TESTS=SRC/'test/java';DB=ROOT/'services/clinical-core/db/migration'
CLASSPATH=str(CLASSES)
results=[]

def run(name,argv,timeout=120):
    try:
        c=subprocess.run(argv,cwd=ROOT,text=True,capture_output=True,timeout=timeout)
        output=c.stdout+'\n---- STDERR ----\n'+c.stderr
        record={'name':name,'exit_code':c.returncode,'passed':c.returncode==0,'command':list(map(str,argv)),'stdout_file':f'evidence/v510/{name}.log'}
    except subprocess.TimeoutExpired as exc:
        output=f'TIMEOUT after {timeout} seconds: {exc}'
        record={'name':name,'exit_code':None,'passed':False,'timeout_seconds':timeout,'command':list(map(str,argv)),'stdout_file':f'evidence/v510/{name}.log'}
    (EV/(name+'.log')).write_text(output,encoding='utf8')
    results.append(record)
    return record

main_files=sorted(map(str,MAIN.rglob('*.java')));test_files=sorted(map(str,TESTS.rglob('*.java')))
print('Sources:',len(main_files),'test:',len(test_files),flush=True)
compile=run('javac_full_source_and_tests',['javac','-encoding','UTF-8','-d',CLASSPATH,*main_files,*test_files],180)
if not compile['passed']:
    (EV/'execution.json').write_text(json.dumps({'results':results,'verified_feature_count':0},indent=2)+'\n')
    print('FAIL full Java compile; no feature batches verified',flush=True);sys.exit(1)

for i in range(94,154):
    batch=f'P06-B{i:04d}'
    result=run(batch,['java','-cp',CLASSPATH,'org.synqoria.triage.TriageWorkflowBatchesTest',batch],25)
    if not result['passed'] or f'{batch} PASS' not in (EV/(batch+'.log')).read_text():
        print('FAIL',batch,flush=True)
    elif i%10==3:print('Individually verified',i-93,'of 60',flush=True)

run('triage_60_feature_regression',['java','-cp',CLASSPATH,'org.synqoria.triage.TriageWorkflowBatchesTest'],30)
run('triage_full_journey',['java','-cp',CLASSPATH,'org.synqoria.triage.TriageJourneyIntegrationTest'],30)
run('triage_sql_adversarial',['python','-m','unittest','discover','-s','tests','-p','test_triage_migration_gate.py','-v'],30)
run('legacy_appointment_sql_adversarial',['python','-m','unittest','discover','-s','tests','-p','appointment_migration_gate_test.py','-v'],30)
run('flyway_inventory',['python','tools/migration_inventory_gate.py',str(DB)],30)
run('flyway_checksums',['python','tools/migration_checksum_gate.py',str(DB),'registers/migration-sha256-lock.json'],30)
run('appointment_sql_audit',['python','tools/appointment_migration_gate.py',str(DB)],30)
run('triage_sql_audit',['python','tools/triage_migration_gate.py',str(DB/'V015__triage_workflow_quarantine.sql')],30)
run('npm_ux_core',['npm','run','check:ux'],90)
run('npm_source_contracts',['npm','run','check:source'],90)
run('existing_source_register_checks',['npm','run','check:registers'],90)
for klass in ['org.synqoria.scheduling.AppointmentLocalTimeResolverTest',
              'org.synqoria.scheduling.AppointmentWindowConflictPolicyTest',
              'org.synqoria.scheduling.AppointmentBookingHorizonPolicyTest',
              'org.synqoria.scheduling.AppointmentPatientConflictPolicyTest',
              'org.synqoria.scheduling.AppointmentTransitionServiceTest']:
    # Confirm class was in original source instead of fabricating a class path.
    path=ROOT/'services/clinical-core/src/main/java'/Path(klass.replace('.','/')+'.java')
    if path.exists():run('legacy_'+klass.split('.')[-1],['java','-cp',CLASSPATH,klass],60)
    else:
        path2=ROOT/'services/clinical-core/src/test/java'/Path(klass.replace('.','/')+'.java')
        if path2.exists():run('legacy_'+klass.split('.')[-1],['java','-cp',CLASSPATH,klass],60)

batch_results={r['name']:r for r in results if re.fullmatch(r'P06-B\d{4}',r['name'])}
verified=[k for k,v in batch_results.items() if v['passed'] and (k+' PASS') in (EV/(k+'.log')).read_text()]
report={'checkpoint':'v0.51.0','claim_scope':'60 distinct M03 domain-workflow and scoped-read feature implementations, not production/clinical certification',
 'source_archive':'v0.50.0 SHA-256 verified before work; see checkpoint verification',
 'batches_requested':60,'batches_individually_tested':len(batch_results),'batches_verified':len(verified),'batches_remaining':60-len(verified),
 'batches_verified_ids':verified,'java_main_sources':len(main_files),'java_test_sources':len(test_files),
 'real_postgresql_executed':False,'clinical_signoff':False,'native_mobile_build_executed':False,
 'results':results}
(EV/'execution.json').write_text(json.dumps(report,indent=2)+'\n')
print('FINAL verified',len(verified),'of 60; other checks',sum(1 for r in results if r['passed']), '/',len(results),flush=True)
if len(verified)!=60:sys.exit(2)
if any(not r['passed'] for r in results):sys.exit(3)
