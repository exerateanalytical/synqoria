#!/usr/bin/env python3
"""Record executed engineering regressions honestly; a failed command stays failed."""
import json,datetime,subprocess,sys,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/v620';OUT.mkdir(exist_ok=True,parents=True)
CLASSES=str(ROOT/'.v620-classes')
scenarios={
 'java_all_domain_operations':['java','-ea','-cp',CLASSES,'org.synqoria.longitudinal.ClinicalVerticalBatchTest','all'],
 'java_adversarial':['java','-ea','-cp',CLASSES,'org.synqoria.longitudinal.ClinicalBoundaryAdversarialTest'],
 'java_jdbc_protocol':['java','-ea','-cp',CLASSES,'org.synqoria.longitudinal.LongitudinalJdbcContractTest','all'],
 'java_snapshot_crypto':['java','-ea','-cp',CLASSES,'org.synqoria.longitudinal.LongitudinalDurabilityTest','all'],
 'java_jdbc_http':['java','-ea','-cp',CLASSES,'org.synqoria.longitudinal.LongitudinalJDBCWireLoopbackTest'],
 'java_supplemental_http':['java','-ea','-cp',CLASSES,'org.synqoria.longitudinal.LongitudinalSupplementalReadsTest','all'],
 'ux_core':['npm','run','check:ux'],
 'source_safety':['npm','run','check:source'],
 'canonical_registers':['npm','run','check:registers'],
 'status_matrix':['python','tools/verify_status_matrix.py'],
 'migration_inventory':['python','tools/migration_inventory_gate.py','services/clinical-core/db/migration'],
 'migration_checksum':['python','tools/migration_checksum_gate.py','services/clinical-core/db/migration','registers/migration-sha256-lock.json'],
 'quarantined_schema':['python','-m','unittest','tests.test_v620_quarantine','-v'],
 'openapi_contract':['python','-m','unittest','tests.test_longitudinal_contract','-v'],
}
for n in range(61,69):scenarios[f'legacy_longitudinal_HTTP_{n}']=['java','-ea','-cp',CLASSES,'org.synqoria.longitudinal.LongitudinalHttpBoundaryTest',f'P07-B{n:04d}']
results={};passed=0
for name,command in scenarios.items():
 try:
  p=subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=120)
  output='command='+json.dumps(command)+'\nexit_code='+str(p.returncode)+'\nSTDOUT:\n'+p.stdout+'\nSTDERR:\n'+p.stderr
  rc=p.returncode
 except subprocess.TimeoutExpired as exc:
  rc=124;output='TIMEOUT '+str(exc)
 logfile=OUT/f'regression-{name}.log';logfile.write_text(output)
 results[name]={'command':command,'exit_code':rc,'passed':rc==0,'log':str(logfile.relative_to(ROOT)),
  'sha256':hashlib.sha256(logfile.read_bytes()).hexdigest()}
 if rc==0:passed+=1
 print(f'{name}: '+('PASS' if rc==0 else 'FAILED'),flush=True)
summary={'version':'0.62.0','run_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'total_groups':len(results),'passed_groups':passed,'failed_groups':len(results)-passed,
 'tests':results,'real_database_tests':0,'clinical_qualification':'NOT_RUN'}
(OUT/'regressions.json').write_text(json.dumps(summary,indent=2)+'\n')
print('REGRESSIONS:',passed,'/',len(results))
if passed!=len(results):sys.exit(1)
