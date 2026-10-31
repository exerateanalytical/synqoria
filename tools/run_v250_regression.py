"""Capture executed regression commands, actual stdout/stderr and fail-closed status."""
from pathlib import Path
import subprocess,datetime,json,shlex,os
r=Path(__file__).resolve().parents[1]
checks=[
 ('ux-core', ['npm','run','check:ux']),
 ('java-full', ['bash','tools/check_java_security.sh']),
 ('source-contracts', ['npm','run','check:source']),
 ('registers', ['npm','run','check:registers']),
 ('sql-migration-audit', ['python','tools/audit_postgres_migrations.py']),
 ('sql-review-audit', ['python','tests/test_v250_review_audit_ddl.py']),
 ('engineering-probes', ['python','tests/test_v250_engineering_probes.py','-v']),
 ('canonical-matrix', ['python','tools/verify_status_matrix.py']),
 ('batch-ledger', ['python','tests/test_batch_ledger.py']),
 ('contract-schema', ['python','tests/test_contract.py']),
 ('sql-scope', ['python','tests/test_sql_scope_foundation.py']),
 ('sql-outbox', ['python','tests/test_sql_outbox.py']),
 ('sql-identity', ['python','tests/test_sql_identity.py']),
 ('event-envelope', ['python','tests/test_event_envelope.py']),
 ('browser-v240-compat', ['bash','-lc','for b in P02-B0078 P02-B0079 P02-B0080 P02-B0081 P02-B0082; do python tests/test_v240_browser.py "$b" || exit 1; done']),
 ('clinical-release-gate-expected-denial', ['python','tools/verify_release_gate.py','--request-release']),
 ('live-postgres-probe-expected-block', ['python','tools/probe_postgres_readiness.py']),
 ('app-build-prerequisite-expected-block', ['python','tools/probe_application_builds.py']),
]
part=os.getenv('SYNQORIA_REGRESSION_PART','all')
if part=='core':checks=checks[:14]
elif part=='browser':checks=checks[14:]
results=[]
for label,cmd in checks:
 try:
  p=subprocess.run(cmd,cwd=r,text=True,capture_output=True,timeout=35)
  row={'id':label,'command':shlex.join(cmd),'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr}
 except subprocess.TimeoutExpired as e:
  row={'id':label,'command':shlex.join(cmd),'exit_code':None,'timed_out':True,'stdout':str(e.stdout),'stderr':str(e.stderr)}
 row['expected_outcome']='DENIED' if label=='clinical-release-gate-expected-denial' else 'BLOCKED' if 'expected-block' in label else 'PASS'
 row['gate_met']=(row['exit_code']==0) if row['expected_outcome']=='PASS' else (row['exit_code']==3 if row['expected_outcome']=='DENIED' else row['exit_code']==4)
 results.append(row)
 print(label,'exit',row['exit_code'],'gate_met',row['gate_met'])
report={'checkpoint':'v0.25.0','recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Unit/mock JDBC/source/static Chromium only; PostgreSQL/Next/Expo/device/clinical release not verified','results':results,'expected_gates_met':all(x['gate_met'] for x in results)}
(r/('evidence/batches/v250-regression-'+part+'.json')).write_text(json.dumps(report,indent=2)+'\n')
if not report['expected_gates_met']:raise SystemExit(1)
