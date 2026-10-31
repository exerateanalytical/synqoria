#!/usr/bin/env python3
"""Run exactly ten distinct, meaningful v0.26.0 implementation tests, record truth."""
from pathlib import Path
import subprocess,datetime,json,sys
ROOT=Path(__file__).resolve().parents[1]
BATCHES=[
 ('P01-B0036',['python','tests/test_v260_foundations.py','Foundation.test_runner','-v'],'offline command evidence runner, exit/timeout/missing-command handling'),
 ('P01-B0037',['python','tests/test_v260_foundations.py','Foundation.test_lock','-v'],'dependency lock and exact pin fail-closed release policy'),
 ('P01-B0038',['python','tests/test_v260_foundations.py','Foundation.test_ci','-v'],'CI actual Next/Expo build jobs, no bypass on missing lock'),
 ('P02-B0086',['node','--test','packages/ux-core/tests/v260-native-more.test.cjs'],'local-only native More policy, bilingual and role scoped'),
 ('P02-B0087',['python','tests/test_v260_native_sources.py','Native.test_staff','-v'],'staff native More panel with accessible local status and locked clinical tabs'),
 ('P02-B0088',['python','tests/test_v260_native_sources.py','Native.test_patient','-v'],'patient/proxy native More panel, background reset and locked clinical tabs'),
 ('P05-B0058',['java','-ea','-cp','/tmp/synqoria-v26-classes','org.synqoria.registration.V260ReviewerGateTest','P05-B0058'],'read/append-only restricted reviewer role privilege attestation'),
 ('P05-B0059',['python','tests/test_v260_replay_ddl.py','Replay.test_unique_command_per_tenant','-v'],'unapplied tenant/facility scoped unique command index migration'),
 ('P05-B0060',['java','-ea','-cp','/tmp/synqoria-v26-classes','org.synqoria.registration.V260ReviewerGateTest','P05-B0060'],'review command timestamp/identity/digest freshness checks'),
 ('P05-B0061',['java','-ea','-cp','/tmp/synqoria-v26-classes','org.synqoria.registration.V260ReviewerGateTest','P05-B0061'],'restricted transaction privilege+freshness guard, rollback and no-commit'),
]
def main():
 results=[];outdir=ROOT/'evidence/batches';outdir.mkdir(parents=True,exist_ok=True)
 for batch,cmd,scope in BATCHES:
  try:
   p=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True,timeout=100)
   result={'id':batch,'scope':scope,'argv':cmd,'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr,'implemented':p.returncode==0,'tested':p.returncode==0,'clinically_reviewed':False,'accepted':False}
  except subprocess.TimeoutExpired as e:
   result={'id':batch,'scope':scope,'argv':cmd,'exit_code':124,'stdout':str(e.stdout or ''),'stderr':'TIMEOUT','implemented':False,'tested':False,'clinically_reviewed':False,'accepted':False}
  results.append(result)
  (outdir/(batch+'-v260.log')).write_text('COMMAND '+str(cmd)+'\nEXIT '+str(result['exit_code'])+'\nSTDOUT\n'+result['stdout']+'\nSTDERR\n'+result['stderr'])
 payload={'version':'0.26.0','executed_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'source/unit/mock-JDBC only; no production PostgreSQL, Next/Expo build or clinical acceptance','target':10,'completed':sum(x['tested'] for x in results),'batches':results}
 (outdir/'P01-P02-P05-v260-ten-batches.json').write_text(json.dumps(payload,indent=2)+'\n')
 print('COMPLETED',payload['completed'],'OF',payload['target'])
 for r in results:print(r['id'],r['exit_code'],r['scope'])
 return 0 if payload['completed']==10 else 1
if __name__=='__main__':sys.exit(main())
