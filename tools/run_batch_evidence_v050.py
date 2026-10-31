"""Run ten independent batch acceptance tests; record actual exit codes and stdout/stderr."""
from pathlib import Path
import subprocess,datetime,json
root=Path(__file__).resolve().parents[1]
java=['java','-ea','-cp','/tmp/synqoria-security-classes']
checks=[
 ('P01-B0004',['python','tests/test_sql_outbox.py'],'static_sql_only'),
 ('P01-B0005',['python','tests/test_sql_identity.py'],'static_sql_only'),
 ('P01-B0006',java+['org.synqoria.events.IdempotencyGuardTest'],'java_unit_in_memory'),
 ('P01-B0007',java+['org.synqoria.events.OutboxClaimLedgerTest'],'java_unit_in_memory'),
 ('P02-B0005',['node','--test','packages/ux-core/tests/route-boundary.test.cjs'],'typescript_unit'),
 ('P02-B0006',['node','--test','packages/ux-core/tests/patient-switch-coordinator.test.cjs'],'typescript_async_unit'),
 ('P03-B0004',java+['org.synqoria.security.TrustedSessionAdmissionTest'],'java_unit_mock_verifier'),
 ('P03-B0005',java+['org.synqoria.consent.ConsentScopePolicyTest'],'java_unit_no_legal_approval'),
 ('P04-B0002',java+['org.synqoria.facility.FacilityLifecycleTest'],'java_unit_in_memory'),
 ('P04-B0003',java+['org.synqoria.facility.CellRouteDirectoryTest'],'java_unit_no_network'),
]
results=[]
for batch,cmd,scope in checks:
 p=subprocess.run(cmd,cwd=root,capture_output=True,text=True,timeout=40)
 results.append({'batch':batch,'command':cmd,'scope':scope,'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr,'verified':p.returncode==0})
 print(batch,scope,'exit',p.returncode)
report={'timestamp_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'preconditions':'Full regression ran before this: Java compiled with javac 21; TypeScript compiled with tsc; SQL source checked only',
 'batch_target':10,'batches_passed':sum(x['verified'] for x in results),
 'batches_failed':sum(not x['verified'] for x in results),
 'scope':'independent source/static/unit checks; NO PostgreSQL, OIDC signature, Next/Expo, HTTP or clinical approval',
 'results':results}
(root/'evidence/batches/P01-P04-v050-ten-batches.json').write_text(json.dumps(report,indent=2)+'\n')
if report['batches_passed']!=10:raise SystemExit(1)
