#!/usr/bin/env python3
"""Execute exactly the 20 P06-B0044..0063 tests; no implied live PostgreSQL or clinical acceptance."""
from pathlib import Path
import subprocess,json,datetime,re,shutil
root=Path(__file__).resolve().parents[1]
classes=Path('/mnt/data/synqoria_run/v410_classes')
classes.mkdir(parents=True,exist_ok=True)
java_sources=sorted(str(p.relative_to(root)) for p in (root/'services/clinical-core/src/main/java').rglob('*.java'))
java_tests=sorted(str(p.relative_to(root)) for p in (root/'services/clinical-core/src/test/java').rglob('*.java'))
compile_cmd=['javac','-d',str(classes),*java_sources,*java_tests]
compiled=subprocess.run(compile_cmd,cwd=root,capture_output=True,text=True,timeout=120)
checks={
 44:('appointment scoped SQL and transition CAS','org.synqoria.scheduling.AppointmentSchemaAlignmentTest'),
 45:('locked patient-scoped snapshot','org.synqoria.scheduling.AppointmentLockedSnapshotTest'),
 46:('idempotency quarantine migration','tests/test_v410_idempotency_sql.py'),
 47:('durable idempotency JDBC adapter','org.synqoria.scheduling.AppointmentIdempotencyStoreTest'),
 48:('audit chain append JDBC adapter','org.synqoria.scheduling.AppointmentAuditStoreTest'),
 49:('outbox append JDBC adapter','org.synqoria.scheduling.AppointmentOutboxStoreTest'),
 50:('clinical access admission gate','org.synqoria.scheduling.AppointmentCommandAdmissionTest'),
 51:('restricted serializable transaction','org.synqoria.scheduling.AppointmentRestrictedTransactionTest'),
 52:('server-side canonical command digest','org.synqoria.scheduling.AppointmentTransitionCommandTest'),
 53:('atomic appointment transition orchestrator','org.synqoria.scheduling.AppointmentTransitionServiceTest'),
 54:('migration/JDBC source schema alignment','tests/test_v410_schema_alignment.py'),
 55:('four-table RLS and non-owner role proof','org.synqoria.scheduling.AppointmentTableRoleFenceTest'),
 56:('server-keyed actor pseudonym','org.synqoria.scheduling.AppointmentActorPseudonymTest'),
 57:('scoped audit chain integrity readback','org.synqoria.scheduling.AppointmentAuditIntegrityReaderTest'),
 58:('scoped administrative status read','org.synqoria.scheduling.AppointmentStatusReadServiceTest'),
 59:('atomic transition failure matrix','org.synqoria.scheduling.AppointmentTransitionFailureMatrixTest'),
 60:('same-transaction four-record consistency proof','org.synqoria.scheduling.AppointmentCommitProofTest'),
 61:('fresh-connection bounded transaction retry','org.synqoria.scheduling.AppointmentFreshConnectionRetryTest'),
 62:('bounded transaction-local SQL timeouts','org.synqoria.scheduling.AppointmentSqlBudgetTest'),
 63:('domain/SQL exhaustive lifecycle parity','org.synqoria.scheduling.AppointmentLifecycleParityTest'),
}
results=[]
for n,(scope,entry) in checks.items():
 cmd=['python',entry] if entry.endswith('.py') else ['java','-ea','-cp',str(classes),entry]
 if compiled.returncode:
  r={'batch':f'P06-B{n:04d}','scope':scope,'command':cmd,'exit_code':None,'verified':False,'blocker':'Java compile failed'}
 else:
  p=subprocess.run(cmd,cwd=root,capture_output=True,text=True,timeout=30)
  r={'batch':f'P06-B{n:04d}','scope':scope,'command':cmd,'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr,'verified':p.returncode==0,
     'test_type':'source_static_only' if entry.endswith('.py') else 'java_unit_or_mock_jdbc'}
 results.append(r)
 print(r['batch'],r.get('exit_code'),r.get('stdout','').strip()[:120])
report={'version':'0.41.0','source_version':'0.40.0','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'target_batches':20,'completed_batches':sum(r['verified'] for r in results),'remaining_batches':sum(not r['verified'] for r in results),
 'compile':{'command':'javac -d <temporary build dir> <all clinical-core main/test java sources>',
            'main_source_count':len(java_sources),'test_source_count':len(java_tests),'exit_code':compiled.returncode,
            'stdout':compiled.stdout,'stderr':compiled.stderr},
 'limitations':['No PostgreSQL server, restricted-role RLS, native app, production OIDC, independent clinical sign-off or HTTP endpoint execution',
                'Migration V014 source/static checked only; no SQL applied'],
 'results':results}
path=root/'evidence/batches/v410-executed.json';path.write_text(json.dumps(report,indent=2)+'\n')
if compiled.returncode or any(not r['verified'] for r in results):raise SystemExit(1)
