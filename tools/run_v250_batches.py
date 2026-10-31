"""Individually execute ten v0.25.0 batches; preserve actual command outputs and exit codes."""
from pathlib import Path
import subprocess,datetime,json,sys,shlex
ROOT=Path(__file__).resolve().parents[1]
TEST='org.synqoria.registration.V250ReviewTransactionTest'
JAVA=['java','-ea','-cp','/tmp/synqoria-v25-classes',TEST]
BATCHES=[
 ('P01-B0033',['python','tests/test_v250_engineering_probes.py','Checks.test_migrations','-v'],'migration static safety and negative mutation tests'),
 ('P01-B0034',['python','tests/test_v250_engineering_probes.py','Checks.test_pg_probe','-v'],'restricted-role PostgreSQL diagnostic probe with mocked psql; live probe blocked'),
 ('P01-B0035',['python','tests/test_v250_engineering_probes.py','Checks.test_build_probe','-v'],'Next/Expo build readiness diagnosis and synthetic installed dependency fixture'),
 ('P02-B0084',['python','tests/test_v250_web_shell.py','P02-B0084'],'accessible nonclinical More menu, locale and Escape focus restoration'),
 ('P02-B0085',['python','tests/test_v250_web_shell.py','P02-B0085'],'nine widths and French 200% reflow including More panel'),
 ('P05-B0053',['bash','-lc','python tests/test_v250_review_audit_ddl.py && java -ea -cp /tmp/synqoria-v25-classes '+TEST+' P05-B0053'],'unapplied audit/outbox relational schema, FORCE RLS and public revoke'),
 ('P05-B0054',JAVA+['P05-B0054'],'restricted PostgreSQL role and FORCE-RLS JDBC metadata guard'),
 ('P05-B0055',JAVA+['P05-B0055'],'scoped serializable reviewer decision row lock and history read'),
 ('P05-B0056',JAVA+['P05-B0056'],'atomic three-statement event/outbox/audit append and rollback tests'),
 ('P05-B0057',JAVA+['P05-B0057'],'restricted reviewer transaction integration and failure rollback'),
]
def run():
    evidence=ROOT/'evidence/batches';evidence.mkdir(exist_ok=True,parents=True)
    results=[]
    for batch,cmd,scope in BATCHES:
        try:
            p=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True,timeout=90)
            result={'batch':batch,'command':shlex.join(cmd),'exit_code':p.returncode,'verified':p.returncode==0,'stdout':p.stdout,'stderr':p.stderr,
                    'scope':scope,'verification_scope':'unit/mock-JDBC/static SQL or Chromium static specimen ONLY; no live PostgreSQL, Next/Expo compilation or clinical acceptance'}
        except subprocess.TimeoutExpired as e:
            result={'batch':batch,'command':shlex.join(cmd),'exit_code':None,'verified':False,'timeout':True,'stdout':str(e.stdout),'stderr':str(e.stderr),'scope':scope}
        path=evidence/('v250-'+batch+'.log')
        path.write_text('BATCH: '+batch+'\nCOMMAND: '+result['command']+'\nEXIT: '+str(result['exit_code'])+'\nSTDOUT:\n'+result['stdout']+'\nSTDERR:\n'+result['stderr']+'\n')
        result['evidence_log']='evidence/batches/'+path.name
        print(batch,'PASS' if result['verified'] else 'FAIL',result['exit_code'])
        results.append(result)
    report={'checkpoint':'v0.25.0','source_checkpoint':'v0.24.0','batch_target':10,'batches_passed':sum(x['verified'] for x in results),
            'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'results':results}
    (evidence/'P01-P02-P05-v250-ten-batches.json').write_text(json.dumps(report,indent=2)+'\n')
    return 0 if report['batches_passed']==10 else 1
if __name__=='__main__':sys.exit(run())
