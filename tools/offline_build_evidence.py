#!/usr/bin/env python3
"""Run actual available source builds/tests with per-command logs; never certify app builds."""
import json, subprocess, hashlib, datetime, sys, pathlib, shutil
ROOT=pathlib.Path(__file__).resolve().parents[1]
COMMANDS=[('ux-typescript',['tsc','-p','packages/ux-core/tsconfig.json']),
          ('ux-unit',['node','--test',*map(str,sorted(pathlib.Path('packages/ux-core/tests').glob('*.test.cjs')))]),
          ('java-unit',['bash','tools/check_java_security.sh']),
          ('source',['npm','run','check:source'])]
def execute(commands=COMMANDS,root=ROOT):
 results=[]
 for name,cmd in commands:
  try:
   run=subprocess.run(cmd,cwd=root,text=True,capture_output=True,timeout=120)
   results.append({'name':name,'argv':cmd,'exit_code':run.returncode,'stdout':run.stdout[-30000:],'stderr':run.stderr[-12000:]})
  except (OSError,subprocess.TimeoutExpired) as exc:
   results.append({'name':name,'argv':cmd,'exit_code':124 if isinstance(exc,subprocess.TimeoutExpired) else 127,'stdout':'','stderr':type(exc).__name__})
 return {'executed_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source':'local source; NOT Next.js or Expo build','checks':results,'passed':all(r['exit_code']==0 for r in results)}
if __name__=='__main__':
 result=execute();out=ROOT/'evidence/batches/v260-offline-commands.json';out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'passed':result['passed'],'checks':[(r['name'],r['exit_code']) for r in result['checks']]}));sys.exit(0 if result['passed'] else 1)
