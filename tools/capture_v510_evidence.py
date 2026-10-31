"""Individually execute and record each P06 M03 feature test. No artificial PASS entries."""
from pathlib import Path
import subprocess,json,sys
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'evidence/v510'; DEST.mkdir(parents=True,exist_ok=True)
cp=str(DEST/'classes')
records=[]
for i in range(94,154):
    bid=f'P06-B{i:04d}'
    cmd=['java','-cp',cp,'org.synqoria.triage.TriageWorkflowBatchesTest',bid]
    try:
        completed=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=20)
        good=completed.returncode==0 and f'{bid} PASS' in completed.stdout
        stdout=completed.stdout+'\n-- STDERR --\n'+completed.stderr
        exit_code=completed.returncode
    except subprocess.TimeoutExpired:
        good=False;stdout='TIMEOUT';exit_code=None
    (DEST/(bid+'.log')).write_text(stdout)
    records.append({'batch_id':bid,'pass':good,'exit_code':exit_code,'evidence':f'evidence/v510/{bid}.log','command':' '.join(cmd)})
    if i%10==3:print('verified',sum(v['pass'] for v in records),'/',len(records),flush=True)
result={'requested':60,'executed':60,'verified':sum(v['pass'] for v in records),'remaining':60-sum(v['pass'] for v in records),
 'scope':'independent source-level domain/read feature tests; not PostgreSQL, UI/device, security audit or clinical release tests',
 'batch_results':records}
(DEST/'individual-batch-results.json').write_text(json.dumps(result,indent=2)+'\n')
if result['verified']!=60:sys.exit(1)
