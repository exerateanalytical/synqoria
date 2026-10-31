from pathlib import Path
import subprocess,json,sys
r=Path(__file__).resolve().parents[1]
files=sorted(x for x in (r/'tests').glob('test_*.py') if 'browser' not in x.name)
checkpoint=r/'evidence/batches/v200-python-regression.json'
results=json.loads(checkpoint.read_text())['results'] if checkpoint.exists() else []
done={x['file'] for x in results}
for f in files:
 if f.name in done:continue
 try:
  p=subprocess.run(['python',str(f)],cwd=r,capture_output=True,text=True,timeout=4)
  results.append(dict(file=f.name,exit=p.returncode,stdout=p.stdout.strip()[-250:],stderr=p.stderr.strip()[-400:]))
 except subprocess.TimeoutExpired:results.append(dict(file=f.name,exit=124,stderr='timeout 4 seconds'))
 (r/'evidence/batches/v200-python-regression.json').write_text(json.dumps(dict(passed=sum(x['exit']==0 for x in results),total=len(results),complete=len(results)==len(files),results=results),indent=2)+'\n')
 print(f.name,results[-1]['exit'],flush=True)
