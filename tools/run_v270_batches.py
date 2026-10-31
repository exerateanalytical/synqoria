#!/usr/bin/env python3
"""Execute ten distinct implemented v0.27.0 batches with truthful per-batch logs."""
from pathlib import Path
import subprocess,datetime,json,sys
ROOT=Path(__file__).resolve().parents[1]
BATCHES=[
 ('P01-B0039',['python','tests/test_v270_engineering.py','Engineering.test_pins','-v'],'exact external dependency pins, staff/patient Expo manifest coherence; compatibility still unverified'),
 ('P01-B0040',['python','tests/test_v270_engineering.py','Engineering.test_lock_integrity','-v'],'strict lockfile workspace, SHA512 and trusted resolution validation; missing real lock blocks builds'),
 ('P01-B0041',['python','tests/test_v270_engineering.py','Engineering.test_build_attempt_fail_closed','-v'],'real Next/Expo build command orchestrator with blocked prerequisite evidence'),
 ('P01-B0042',['python','tests/test_v270_engineering.py','Engineering.test_archive_integrity','-v'],'cumulative ZIP path, membership and digest verification against tampering'),
 ('P02-B0089',['python','tests/test_v270_shell_sources.py','Shell.test_more_focus','-v'],'web More keyboard focus, Escape/outside dismissal and capability-locked navigation'),
 ('P02-B0090',['python','tests/test_v270_browser.py','P02-B0090'],'responsive fixed mobile More disclosure, safe scrolling and focus; Chromium static specimen'),
 ('P02-B0091',['python','tests/test_v270_shell_sources.py','Shell.test_locale_propagation','-v'],'EN/FR URL propagation between three web shells without persisting clinical state'),
 ('P02-B0092',['python','tests/test_v270_browser.py','P02-B0092'],'Chromium nine-width French, 200% typography, locked action and four mobile destinations; static specimen'),
 ('P05-B0062',['java','-ea','-cp','/tmp/synqoria-v27-classes','org.synqoria.registration.V270EvidenceReconciliationTest','P05-B0062'],'tenant/facility scoped event-audit-outbox integrity reconciliation; mock JDBC'),
 ('P05-B0063',['java','-ea','-cp','/tmp/synqoria-v27-classes','org.synqoria.registration.V270EvidenceReconciliationTest','P05-B0063'],'serializable tenant/facility reviewer command replay preflight; mock JDBC only'),
]
def main():
 outdir=ROOT/'evidence/batches';outdir.mkdir(exist_ok=True,parents=True);results=[]
 for batch,cmd,scope in BATCHES:
  try:
   p=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True,timeout=120)
   r={'id':batch,'scope':scope,'argv':cmd,'exit_code':p.returncode,'stdout':p.stdout[-12000:],'stderr':p.stderr[-12000:]}
  except subprocess.TimeoutExpired as e:r={'id':batch,'scope':scope,'argv':cmd,'exit_code':124,'stdout':str(e.stdout or '')[-12000:],'stderr':'TIMEOUT'}
  except OSError as e:r={'id':batch,'scope':scope,'argv':cmd,'exit_code':127,'stdout':'','stderr':type(e).__name__}
  r.update({'specified':True,'designed':True,'implemented':True,'tested':r['exit_code']==0,'integration_tested':False,'independently_reviewed':False,'clinically_reviewed':False,'accepted':False})
  results.append(r)
  (outdir/(batch+'-v270.log')).write_text('COMMAND '+repr(cmd)+'\nEXIT '+str(r['exit_code'])+'\nSTDOUT\n'+r['stdout']+'\nSTDERR\n'+r['stderr'])
 payload={'version':'0.27.0','source_checkpoint':'v0.26.0','executed_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'target':10,'completed':sum(r['tested'] for r in results),'scope':'source/unit/mock JDBC and static Chromium; NO Next/Expo app builds, PostgreSQL or clinical acceptance','batches':results}
 (outdir/'P01-P02-P05-v270-ten-batches.json').write_text(json.dumps(payload,indent=2)+'\n')
 print('COMPLETED',payload['completed'],'OF',payload['target'])
 for r in results:print(r['id'],r['exit_code'],r['scope'])
 return 0 if payload['completed']==10 else 1
if __name__=='__main__':sys.exit(main())
