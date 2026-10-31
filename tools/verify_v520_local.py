"""Reproducible local v0.52.0 verification. Does not certify live DB, OIDC, KMS, or clinical safety."""
from concurrent.futures import ThreadPoolExecutor,as_completed
import subprocess,pathlib,json,time,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/v520';OUT.mkdir(exist_ok=True,parents=True)
main=ROOT/'services/clinical-core/src/main/java'
tests=ROOT/'services/clinical-core/src/test/java'
sources=sorted(list(main.rglob('*.java'))+list(tests.rglob('*.java')))
(OUT/'java-all-sources.txt').write_text('\n'.join(str(p.relative_to(ROOT)) for p in sources)+'\n')
SCENARIOS='codec-roundtrip codec-determinism codec-bad-magic codec-truncation codec-trailing codec-oversize codec-audit codec-tamper crypto-roundtrip crypto-random-nonce crypto-scope-binding crypto-revision-binding crypto-key-binding crypto-tamper crypto-invalid-key crypto-defensive-copy jdbc-happy-path jdbc-scoped-lock jdbc-cas jdbc-event jdbc-outbox jdbc-ciphertext jdbc-no-autocommit jdbc-serializable jdbc-role jdbc-bypassrls jdbc-rls jdbc-scope-echo jdbc-missing-row jdbc-snapshot-tamper jdbc-stale-revision jdbc-cas-race jdbc-event-failure jdbc-outbox-failure jdbc-cross-tenant jdbc-revoked-evidence jdbc-duplicate-command jdbc-invalid-mutation jdbc-budget jdbc-readonly jdbc-superuser jdbc-missing-tip jdbc-outbox-tip-mismatch jdbc-row-stage-mismatch jdbc-no-manage-capability'.split()
CP=str(OUT/'classes')
checks=[
 ('java-all-compile',['javac','-d',CP,'@'+str(OUT/'java-all-sources.txt')]),
 ('v510-triage-journey',['java','-cp',CP,'org.synqoria.triage.TriageJourneyIntegrationTest']),
 ('ux-core',['npm','run','check:ux']),
 ('canonical-registers',['npm','run','check:registers']),
 ('native-source',['npm','run','check:source']),
 ('v015-triage-sql',['python','-m','unittest','discover','-s','tests','-p','test_triage_migration_gate.py']),
 ('v016-triage-sql',['python','-m','unittest','discover','-s','tests','-p','test_triage_encrypted_migration_gate.py']),
 ('migration-lock',['python','tools/migration_checksum_gate.py','services/clinical-core/db/migration','registers/migration-sha256-lock.json']),
 ('migration-checksum-tests',['python','-m','unittest','discover','-s','tests','-p','migration_checksum_gate_test.py']),
]

def run(entry):
 name,cmd=entry;start=time.monotonic()
 try:
  p=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=75)
  code=p.returncode;output=p.stdout+'\n'+p.stderr
 except subprocess.TimeoutExpired as ex:code=124;output='TIMEOUT '+str(ex)
 (OUT/(name+'.log')).write_text(output)
 return {'name':name,'exit_code':code,'duration_seconds':round(time.monotonic()-start,3),'log':f'evidence/v520/{name}.log'}

if __name__=='__main__':
 results=[]
 for entry in checks:
  r=run(entry);results.append(r);print(r['name'],r['exit_code'],flush=True)
  if r['exit_code']!=0:break
 if all(x['exit_code']==0 for x in results):
  jobs=[('scenario-'+s,['java','-cp',CP,'org.synqoria.triage.TriagePersistenceContractTest',s]) for s in SCENARIOS]
  jobs += [(f'regression-P06-B{n:04d}',['java','-cp',CP,'org.synqoria.triage.TriageWorkflowBatchesTest',f'P06-B{n:04d}']) for n in range(94,154)]
  with ThreadPoolExecutor(max_workers=8) as pool:
   futures=[pool.submit(run,j) for j in jobs]
   for future in as_completed(futures):
    r=future.result();results.append(r)
    if r['exit_code']!=0:print('FAILED',r['name'],flush=True)
 (OUT/'verification-results.json').write_text(json.dumps({'checks':results,'passed':sum(r['exit_code']==0 for r in results),'total':len(results),'scope':'synthetic source and static tests; no live DB/clinical acceptance'},indent=2)+'\n')
 print('VERIFICATION',sum(r['exit_code']==0 for r in results),'/',len(results),flush=True)
 sys.exit(0 if len(results)==len(checks)+len(SCENARIOS)+60 and all(r['exit_code']==0 for r in results) else 1)
