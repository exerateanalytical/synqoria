#!/usr/bin/env python3
"""Execute and record each P07 M04-M06 domain batch independently. Does not certify clinical deployment."""
from pathlib import Path
from datetime import datetime, timezone
import json, subprocess, sys, hashlib, os, tempfile
root=Path(__file__).resolve().parent.parent
out=root/'evidence/v600'
out.mkdir(parents=True,exist_ok=True)
classes=Path(os.environ.get('SYNQORIA_TEST_CLASSES',str(root/'.v600-classes')))
classes.mkdir(parents=True,exist_ok=True)
sources=sorted((root/'services/clinical-core/src/main/java/org/synqoria/longitudinal').glob('*.java')) + sorted((root/'services/clinical-core/src/test/java/org/synqoria/longitudinal').glob('*.java'))
compiled=subprocess.run(['javac','-d',str(classes),*[str(x) for x in sources]],capture_output=True,text=True,cwd=root,timeout=90)
(out/'compile.log').write_text(f'exit_code: {compiled.returncode}\nSTDOUT:\n{compiled.stdout}\nSTDERR:\n{compiled.stderr}\n')
if compiled.returncode: print('compilation failed:',compiled.stderr,file=sys.stderr);sys.exit(2)

java='org.synqoria.longitudinal.ClinicalVerticalBatchTest'
results=[]
for index in range(1,61):
    batch=f'P07-B{index:04d}'
    command=['java','-ea','-cp',str(classes),java,batch]
    completed=subprocess.run(command,cwd=root,text=True,capture_output=True,timeout=20)
    log=f'command: {" ".join(command)}\nexit_code: {completed.returncode}\nSTDOUT:\n{completed.stdout}\nSTDERR:\n{completed.stderr}\n'
    logpath=out/f'{batch}.log'
    logpath.write_text(log,encoding='utf-8')
    successful=completed.returncode==0 and completed.stdout.count('PASS '+batch+' ')==1
    results.append({'id':batch,'operation':completed.stdout.strip().split(' ',2)[-1] if successful else None,'passed':successful,
                    'exit_code':completed.returncode,'log':f'evidence/v600/{batch}.log',
                    'stdout_sha256':hashlib.sha256(completed.stdout.encode()).hexdigest()})
report={'checkpoint':'v0.60.0','generated_at_utc':datetime.now(timezone.utc).isoformat(),
        'target':60,'completed':sum(r['passed'] for r in results),'failed':sum(not r['passed'] for r in results),
        'evidence':'separate Java process with assertions enabled for each unique clinical domain operation',
        'database_integration_qualified':False,'clinical_certified':False,'batches':results}
(out/'individual-batches.json').write_text(json.dumps(report,indent=2)+'\n')
print('individual batches',report['completed'],'/',report['target'],'failures',report['failed'])
if report['failed']:
    print('FAILED', [r['id'] for r in results if not r['passed']]);sys.exit(1)
