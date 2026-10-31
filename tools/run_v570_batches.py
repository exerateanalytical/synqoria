#!/usr/bin/env python3
"""Evidence of individually verified v0.57 M03 engineering features; no formal certification."""
import subprocess,pathlib,json,re,time,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/v570';OUT.mkdir(parents=True,exist_ok=True)
node='packages/ux-core/tests/triage-v57-read-session.test.cjs'
workspace='packages/ux-core/tests/triage-v57-workspace.test.cjs'
batches=[
 ('P06-B0363',['node','--test','--test-name-pattern=scoped GET summary|summary decoder|GET fails closed',node]),
 ('P06-B0364',['node','--test','--test-name-pattern=audit paging|GET history',node]),
 ('P06-B0365',['node','--test','--test-name-pattern=case session|denied command|patient switch|history reads',node]),
 ('P06-B0366',['node','--test','--test-name-pattern=worklist',workspace]),
 ('P06-B0367',['node','--test','--test-name-pattern=audit pager',workspace]),
 ('P06-B0368',['python','-m','unittest','discover','-s','tests','-p','test_triage_v570_read_contract.py','-v']),
]
records=[]
for batch,cmd in batches:
 start=time.monotonic();run=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=40)
 output=run.stdout+'\n'+run.stderr
 (OUT/(batch+'.log')).write_text(output)
 if cmd[0]=='node':
  m=re.search(r'^# pass (\d+)$',output,re.M);passed=int(m.group(1)) if m else 0
 else:passed=len(re.findall(r'\.\.\. ok$',output,re.M))
 records.append({'id':batch,'passed':run.returncode==0 and passed>0,'tests_passed':passed,'exit_code':run.returncode,'duration_seconds':round(time.monotonic()-start,3),'command':cmd,'log':'evidence/v570/'+batch+'.log'})
 print(batch,'PASS' if records[-1]['passed'] else 'FAIL','tests',passed,flush=True)
report={'checkpoint':'v0.57.0','parent':'v0.56.0','target_batches':60,'completed_verified_batches':sum(r['passed'] for r in records),'remaining_target':60-sum(r['passed'] for r in records),'certification':'deferred_not_waived','source_only_ui_not_counted':True,'runs':records}
(OUT/'batch-execution.json').write_text(json.dumps(report,indent=2)+'\n')
sys.exit(0 if all(r['passed'] for r in records) else 2)
