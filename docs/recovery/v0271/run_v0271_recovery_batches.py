#!/usr/bin/env python3
"""Individual, honest P06 batch evidence for v0.27.1 recovery branch; no PostgreSQL."""
from pathlib import Path
import subprocess,json,datetime,sys
r=Path(__file__).resolve().parents[1]
source=r/'services/clinical-core/src/main/java';tests=r/'services/clinical-core/src/test/java';classes='/tmp/synqoria-v0271-classes'
files=sorted(map(str,source.rglob('*.java')))+sorted(map(str,tests.rglob('*.java')))
compile=subprocess.run(['javac','-d',classes,*files],cwd=r,text=True,capture_output=True,timeout=120)
if compile.returncode:print(compile.stderr[-8000:]);sys.exit(compile.returncode)
records=[]
for x in range(10,20):
 batch=f'P06-B{x:04d}';cmd=['java','-ea','-cp',classes,'org.synqoria.scheduling.V030SchedulingRecoveryTest',batch]
 try:
  proc=subprocess.run(cmd,cwd=r,text=True,capture_output=True,timeout=25)
  code=proc.returncode;out=proc.stdout;err=proc.stderr
 except subprocess.TimeoutExpired as e:code=124;out=str(e.stdout or '');err='TIMEOUT'
 rec={'batch':batch,'command':cmd,'exit_code':code,'stdout':out.strip(),'stderr':err[-5000:],'tested':code==0,'scope':'unit/mock JDBC and DDL source only','clinical_certified':False,'actual_postgres':False}
 records.append(rec)
 (r/f'evidence/batches/{batch}-v0271.log').write_text(' '.join(cmd)+'\nEXIT '+str(code)+'\n'+out+'\n'+err)
 print(batch,code,out.strip(),flush=True)
record={'branch':'v0.27.1-recovery','source':'verified v0.27.0','unavailable_parallel_sources':['v0.28.0','v0.29.0'], 'date_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'batches':records,'passed':sum(a['tested'] for a in records),'target':10}
(r/'evidence/batches/v0271-ten-batches.json').write_text(json.dumps(record,indent=2)+'\n')
sys.exit(0 if record['passed']==10 else 1)
