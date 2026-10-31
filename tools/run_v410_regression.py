"""Run available real source, Java and UX checks with complete stdout/exit evidence."""
from pathlib import Path
import subprocess,json,datetime
root=Path(__file__).resolve().parents[1]
checks=[
 ('java-security-regression',['bash','tools/check_java_security.sh'],180),
 ('ux-typescript-node',['npm','run','check:ux'],120),
 ('navigation-register',['python','tests/test_navigation_contract.py'],60),
 ('canonical-register',['python','tests/test_baseline.py'],60),
 ('status-matrix',['python','tools/verify_status_matrix.py'],60),
 ('batch-ledger',['python','tests/test_batch_ledger.py'],60),
 ('source-style',['python','tests/test_source_safety.py'],60),
 ('source-shells',['python','tests/test_shell_sources.py'],60),
 ('tsx-syntax',['node','tests/test_tsx_syntax.cjs'],60),
 ('api-contract',['python','tests/test_contract.py'],60),
 ('appointment-contract',['python','tests/test_v300_appointment_contract.py'],60),
 ('idempotency-schema',['python','tests/test_v410_idempotency_sql.py'],60),
 ('jdbc-schema-alignment',['python','tests/test_v410_schema_alignment.py'],60),
 ('archive-verifier',['python','tests/test_v410_archive_integrity.py'],60),
]
results=[]
for name,cmd,timeout in checks:
 try:
  p=subprocess.run(cmd,cwd=root,capture_output=True,text=True,timeout=timeout)
  r={'id':name,'command':cmd,'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr}
 except subprocess.TimeoutExpired as ex:
  r={'id':name,'command':cmd,'exit_code':None,'timeout':True,'stdout':str(ex.stdout),'stderr':str(ex.stderr)}
 results.append(r)
 print(name,r['exit_code'],(r['stderr'] or r['stdout'])[-200:].replace('\n',' '))
report={'version':'0.41.0','recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'scope':'Java unit/mock-JDBC, TypeScript UX-core, source/static contracts. NOT real PostgreSQL, Next.js/Expo builds, mobile devices, clinical review or live RLS.',
 'passed':sum(x['exit_code']==0 for x in results),'failed':sum(x['exit_code']!=0 for x in results),'results':results}
(root/'evidence/batches/v410-regression.json').write_text(json.dumps(report,indent=2)+'\n')
if report['failed']:raise SystemExit(1)
