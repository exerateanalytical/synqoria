#!/usr/bin/env python3
"""Run ten separate v0.61.0 engineering verification commands (NOT clinical qualification)."""
from pathlib import Path
import subprocess,hashlib,json,datetime,sys
ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'evidence/v610';OUT.mkdir(parents=True,exist_ok=True)
CLASSES=ROOT/'.v610-classes';CLASSES.mkdir(exist_ok=True)
main=sorted((ROOT/'services/clinical-core/src/main/java').rglob('*.java'))
tests=sorted((ROOT/'services/clinical-core/src/test/java').rglob('*.java'))
compile_command=['javac','-d',str(CLASSES),*[str(p) for p in main+tests]]
compiled=subprocess.run(compile_command,capture_output=True,text=True,cwd=ROOT,timeout=180)
(OUT/'compile.log').write_text('command=javac '+str(len(main)+len(tests))+' Java files\nexit_code='+str(compiled.returncode)+'\n'+compiled.stdout+'\n'+compiled.stderr)
if compiled.returncode:print('Java compilation failed',compiled.stderr,file=sys.stderr);sys.exit(2)
rows=[]
for i in range(61,71):
 batch=f'P07-B{i:04d}'
 if i<=68:command=['java','-ea','-cp',str(CLASSES),'org.synqoria.longitudinal.LongitudinalHttpBoundaryTest',batch]
 elif i==69:command=['python','-m','unittest','discover','-s','tests','-p','test_longitudinal_contract.py','-v']
 else:command=['node','--test','packages/ux-core/tests/longitudinal-client-v61.test.cjs']
 p=subprocess.run(command,cwd=ROOT,timeout=60,capture_output=True,text=True)
 count=1 if i<=68 else 5 if i==69 else 6
 passed=p.returncode==0 and (('PASS '+batch) in p.stdout if i<=68 else ('OK' in p.stderr if i==69 else '# fail 0' in p.stdout and '# tests 6' in p.stdout))
 data='command='+json.dumps(command)+'\nexit_code='+str(p.returncode)+'\nSTDOUT:\n'+p.stdout+'\nSTDERR:\n'+p.stderr
 log=OUT/(batch+'.log');log.write_text(data)
 rows.append({'batch':batch,'implemented_area':{
  61:'strict scoped clinical command JSON decoding',62:'authenticated encounter POST and case summary',
  63:'clinical note creation and confidential note GET',64:'observation recording/verification and filtered GET',
  65:'scope denial and verified admission',66:'audit evidence bounded cursor page',
  67:'idempotent replay and revision conflicts',68:'HTTP request-smuggling/header/IDOR rejection',
  69:'versioned OpenAPI parity and safety contract',70:'TypeScript staff client with context abort and privacy-minimal decoders'}[i],
  'status':'verified_application_integration_test_not_clinically_certified' if passed else 'failed',
  'tests':count,'exit_code':p.returncode,'passed':passed,'log':str(log.relative_to(ROOT)),
  'stdout_sha256':hashlib.sha256(p.stdout.encode()).hexdigest()})
summary={'version':'0.61.0','base':'v0.60.0 PR #3 domain source','target':60,'completed':sum(x['passed'] for x in rows),
'remaining':60-sum(x['passed'] for x in rows),'compile_source_count':len(main),'compile_test_count':len(tests),
'compile_exit_code':compiled.returncode,'batches':rows,'created_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
'live_database_qualified':False,'production_identity_qualified':False,'certification':'DEFERRED_NOT_CERTIFIED'}
(OUT/'verified-batches.json').write_text(json.dumps(summary,indent=2)+'\n')
print(f"Completed {summary['completed']}/{len(rows)} attempted batches; {sum(x['tests'] for x in rows if x['passed'])} focused checks")
for x in rows: print(x['batch'],x['status'],x['exit_code'])
if summary['completed']!=len(rows):sys.exit(1)
