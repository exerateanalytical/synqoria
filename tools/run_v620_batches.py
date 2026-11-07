#!/usr/bin/env python3
"""Run independent v0.62 P07 feature-level engineering checks, not release certification."""
from pathlib import Path
import subprocess,hashlib,json,datetime,sys
ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'evidence/v620';OUT.mkdir(parents=True,exist_ok=True)
CLASSES=ROOT/'.v620-classes';CLASSES.mkdir(exist_ok=True)
main=sorted((ROOT/'services/clinical-core/src/main/java').rglob('*.java'))
tests=sorted((ROOT/'services/clinical-core/src/test/java').rglob('*.java'))
cmd=['javac','-d',str(CLASSES),*[str(x) for x in main+tests]]
compiled=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=180)
(OUT/'compile.log').write_text('files='+str(len(main)+len(tests))+'\nexit='+str(compiled.returncode)+'\n'+compiled.stdout+'\n'+compiled.stderr)
if compiled.returncode:print(compiled.stderr,file=sys.stderr);sys.exit(2)
J=lambda c:['java','-ea','-cp',str(CLASSES),'org.synqoria.longitudinal.LongitudinalDurabilityTest',c]
D=lambda c:['java','-ea','-cp',str(CLASSES),'org.synqoria.longitudinal.LongitudinalJdbcContractTest',c]
H=lambda c:['java','-ea','-cp',str(CLASSES),'org.synqoria.longitudinal.LongitudinalSupplementalReadsTest',c]
SUITES={
 71:('M04/M05/M06','bounded deterministic aggregate snapshot codec',J('snapshot')),
 72:('M05','preservation of signed and immutable note state',J('signed-note')),
 73:('M06','round-trip observation correction chain and status',J('observation-corrections')),
 74:('M04/M05/M06','audit, outbox and idempotency receipt round-trip integrity',J('audit-receipts')),
 75:('M04/M05/M06','encrypted AES-256-GCM clinical aggregate envelopes',J('envelope-aad')),
 76:('M04/M05/M06','patient/facility/encounter/revision authentication via AAD',J('scope-integrity')),
 77:('M04/M05/M06','snapshot tamper, bounds and malformed Unicode rejection',J('snapshot-tampering')),
 78:('M04/M05/M06','transaction-local PostgreSQL scope echo fail-closed',D('scope-echo')),
 79:('M04/M05/M06','encrypted row-locked JDBC case read with local scope',D('read')),
 80:('M04/M05/M06','JDBC persisted audit/receipt/outbox correlation validation',D('missing-receipt')),
 81:('M04/M05/M06','optimistic revision compare-and-swap write denial',D('cas-conflict')),
 82:('M04/M05/M06','atomic aggregate audit receipt outbox JDBC insert contract',D('write')),
 83:('M04/M05/M06','replay-safe database-rehydrated idempotency',D('replay')),
 84:('M04/M05/M06','transaction rollback on outbox write failure',D('atomic-rollback')),
 85:('M04/M05/M06','quarantined V020 restricted-row RLS/append-only DDL', ['python','-m','unittest','tests.test_v620_quarantine','-v']),
 86:('M04','backend authorized planned encounter initialization',D('provision')),
 87:('M04','fail-closed duplicate and revoked provisioning rejection',D('provision-denied')),
 88:('M04/M05/M06','restricted trust-to-JDBC-to-HTTP loopback wiring', ['java','-ea','-cp',str(CLASSES),'org.synqoria.longitudinal.LongitudinalJDBCWireLoopbackTest']),
 89:('M05','patient-scoped note index with confidential visibility and bounded pagination',H('note-index')),
 90:('M06','bidirectional observation correction lineage and bounded HTTP read',H('lineage')),
 91:('M05','TypeScript scoped note index response decoder and GET client',['node','--test','--test-name-pattern=B0091','packages/ux-core/tests/longitudinal-client-v62.test.cjs']),
 92:('M06','TypeScript observation lineage privacy and stale-response guard',['node','--test','--test-name-pattern=B0092','packages/ux-core/tests/longitudinal-client-v62.test.cjs']),
 93:('M04/M05/M06','versioned private read OpenAPI contract and backend parity',['python','-m','unittest','tests.test_longitudinal_contract','-v'])
}
assert len(SUITES)==23
rows=[]
for n,(module,area,command) in SUITES.items():
  batch=f'P07-B{n:04d}'
  try:
    process=subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=65)
    success=process.returncode==0 and ('PASS' in process.stdout or 'OK' in process.stderr or '# fail 0' in process.stdout)
    out=f'command={json.dumps(command)}\nexit_code={process.returncode}\nstdout:\n{process.stdout}\nstderr:\n{process.stderr}'
    code=process.returncode
  except subprocess.TimeoutExpired as exc:
    success=False;code=124;out=f'timed out: {command} {exc}'
  path=OUT/f'{batch}.log';path.write_text(out)
  rows.append({'batch':batch,'module':module,'implementation':area,'test_command':command,'test_exit':code,
    'passed':success,'log':str(path.relative_to(ROOT)),'log_sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
  print(batch,'PASS' if success else 'FAIL',area,flush=True)
summary={'version':'0.62.0','base':'v0.61.0 P07-B0070 PR4 worktree',
 'requested_batched_features':100,'attempted':len(rows),'completed':sum(x['passed'] for x in rows),'remaining_requested':100-sum(x['passed'] for x in rows),
 'compile_source_files':len(main),'compile_test_files':len(tests),'java_compile_exit':compiled.returncode,
 'checks':rows,'run_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'real_postgresql_integration':False,'production_oidc_and_mfa':False,'clinical_certification':'DEFERRED_NOT_PASSED',
 'note':'Test cases are synthetic Java/JDBC protocol and privacy/source contracts; no live PostgreSQL or device test.'}
(OUT/'individual-batch-results.json').write_text(json.dumps(summary,indent=2)+'\n')
print(f"Verified: {summary['completed']}/{len(rows)} attempted, {summary['remaining_requested']}/100 requested not completed")
if not all(x['passed'] for x in rows):sys.exit(1)
