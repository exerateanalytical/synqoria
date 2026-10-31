#!/usr/bin/env python3
"""Attempt REAL application builds only after verified prerequisites; never synthesize a pass."""
import pathlib,subprocess,json,datetime,sys
from dependency_compatibility import audit as pin_audit
from verify_lock_integrity import audit as lock_audit
from probe_application_builds import audit as installed_audit
from attest_app_builds import attest
ROOT=pathlib.Path(__file__).resolve().parents[1]
COMMANDS=[('next-build',['npm','run','build','--workspace','@synqoria/web'],'.'),('staff-expo-export',['npx','--no-install','expo','export','--platform','all'],'apps/staff-mobile'),('patient-expo-export',['npx','--no-install','expo','export','--platform','all'],'apps/patient-mobile')]
def execute(root=ROOT,timeout=240,commands=COMMANDS):
 root=pathlib.Path(root); blockers=pin_audit(root)+lock_audit(root)+installed_audit(root)['issues'];checks=[]
 if blockers:return {'status':'BLOCKED','blockers':sorted(set(blockers)),'checks':[],'builds_passed':0,'clinical_release':False}
 for name,cmd,cwd in commands:
  try:
   p=subprocess.run(cmd,cwd=root/cwd,text=True,capture_output=True,timeout=timeout)
   checks.append({'name':name,'argv':cmd,'exit_code':p.returncode,'stdout':p.stdout[-12000:],'stderr':p.stderr[-12000:]})
  except subprocess.TimeoutExpired as e:checks.append({'name':name,'argv':cmd,'exit_code':124,'stdout':str(e.stdout or '')[-12000:],'stderr':'TIMEOUT'})
  except OSError as e:checks.append({'name':name,'argv':cmd,'exit_code':127,'stdout':'','stderr':type(e).__name__})
 result={'status':'PASSED' if all(c['exit_code']==0 for c in checks) else 'FAILED','blockers':[],'checks':checks,'builds_passed':sum(c['exit_code']==0 for c in checks),'clinical_release':False}
 if result['status']=='PASSED':
  result['artifacts']=attest(root,checks)
  if result['artifacts']['status']!='ATTESTED':result['status']='FAILED';result['blockers'].extend(result['artifacts']['issues'])
 return result
if __name__=='__main__':
 result=execute();result['executed_utc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
 out=ROOT/'evidence/batches/v280-actual-build-attempt.json';out.write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps({'status':result['status'],'blockers':result['blockers'],'builds_passed':result['builds_passed']}));sys.exit(0 if result['status']=='PASSED' else 4)
