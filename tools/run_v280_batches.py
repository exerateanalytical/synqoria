#!/usr/bin/env python3
"""Execute exactly 10 independently testable v0.28 source batches; record real process evidence."""
import datetime,json,pathlib,subprocess,sys,time
ROOT=pathlib.Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'evidence/batches'
EVIDENCE.mkdir(parents=True,exist_ok=True)
BATCHES=[
 ('P01-B0043',['python','tests/test_v280_build_attestation.py'],'Real-file build artifact integrity verifier; synthetic fixtures only'),
 ('P01-B0044',['python','tests/test_v280_ci_candidate.py'],'Manual reviewed-lock proposal CI, fail-closed build prerequisite'),
 ('P02-B0093',['python','tests/test_v280_more_focus.py'],'Compiled TypeScript disclosure controller in real Chromium DOM'),
 ('P02-B0094',['python','tests/test_v280_short_viewport.py'],'Chromium short-viewport/200% EN-FR responsive reflow specimen'),
 ('P06-B0001',['java','-ea','-cp','/tmp/synqoria-v280-classes','org.synqoria.scheduling.AppointmentWindowTest'],'DST-aware local appointment time windows and overlap semantics'),
 ('P06-B0002',['java','-ea','-cp','/tmp/synqoria-v280-classes','org.synqoria.scheduling.AppointmentAggregateTest'],'Appointment lifecycle with optimistic revision and cancellation reasons'),
 ('P06-B0003',['java','-ea','-cp','/tmp/synqoria-v280-classes','org.synqoria.scheduling.ResourceReservationIndexTest'],'Tenant/facility/resource conflict prevention under concurrency (memory only)'),
 ('P06-B0004',['java','-ea','-cp','/tmp/synqoria-v280-classes','org.synqoria.scheduling.AppointmentAccessPolicyTest'],'Trusted tenant/facility/patient/capability authorization'),
 ('P06-B0005',['java','-ea','-cp','/tmp/synqoria-v280-classes','org.synqoria.scheduling.AppointmentCommandFingerprintTest'],'Length-prefixed scoped SHA-256 command fingerprint'),
 ('P06-B0006',['java','-ea','-cp','/tmp/synqoria-v280-classes','org.synqoria.scheduling.AppointmentWorkflowPreflightTest'],'Denied-safe workflow preflight and outbox intent (no DB writes)'),
]

def execute(name,cmd,timeout=70):
    start=time.monotonic()
    try:
        p=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=timeout)
        return {'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr,'duration_seconds':round(time.monotonic()-start,3)}
    except subprocess.TimeoutExpired as e:
        return {'exit_code':124,'stdout':(e.stdout or b'').decode(errors='replace') if isinstance(e.stdout,bytes) else str(e.stdout or ''),
                'stderr':'TIMEOUT','duration_seconds':round(time.monotonic()-start,3)}


def run():
    files=sorted(str(p) for base in ('services/clinical-core/src/main/java','services/clinical-core/src/test/java') for p in (ROOT/base).rglob('*.java'))
    compile_result=execute('javac',['javac','-d','/tmp/synqoria-v280-classes']+files,90)
    (EVIDENCE/'v280-java-compile.log').write_text(json.dumps(compile_result,indent=2)+'\n')
    results=[]
    for name,cmd,scope in BATCHES:
        if name.startswith('P06-') and compile_result['exit_code']!=0:
            result={'exit_code':4,'stdout':'','stderr':'BLOCKED: Java compilation failed','duration_seconds':0}
        else:
            result=execute(name,cmd)
        (EVIDENCE/f'{name}-v280.log').write_text('COMMAND '+json.dumps(cmd)+'\nEXIT_CODE '+str(result['exit_code'])+'\n'+result['stdout']+'\nSTDERR\n'+result['stderr'])
        results.append({'batch':name,'scope':scope,'argv':cmd,'exit_code':result['exit_code'],
                        'verified':result['exit_code']==0,'duration_seconds':result['duration_seconds'],
                        'log':f'evidence/batches/{name}-v280.log','verification_level':'unit_or_static_browser_only'})
    report={'version':'0.28.0','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'batch_target':10,'batches_passed':sum(x['verified'] for x in results),
            'java_compile_exit_code':compile_result['exit_code'],'results':results,
            'clinical_release':False,'app_builds_verified':False,'live_postgres_verified':False}
    (EVIDENCE/'P01-P02-P06-v280-ten-batches.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'target':10,'verified':report['batches_passed'],'java_compile_exit':compile_result['exit_code'],
                      'failures':[x['batch'] for x in results if not x['verified']]},indent=2))
    return 0 if report['batches_passed']==10 else 4
if __name__=='__main__':sys.exit(run())
