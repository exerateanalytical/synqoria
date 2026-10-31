#!/usr/bin/env python3
import subprocess,json,pathlib,time,datetime,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
COMMANDS=[
 ('ux-core',['npm','run','check:ux'],0,90),
 ('source-check',['npm','run','check:source'],0,90),
 ('canonical-registers',['python','tools/verify_status_matrix.py'],0,30),
 ('baseline',['python','tests/test_baseline.py'],0,30),
 ('navigation-contract',['python','tests/test_navigation_contract.py'],0,30),
 ('java-security-regression',['bash','tools/check_java_security.sh'],0,110),
 ('contract-schema',['python','tests/test_contract.py'],0,45),
 ('sql-static-audit',['python','tools/audit_postgres_migrations.py'],0,45),
 ('v270-engineering',['python','tests/test_v270_engineering.py'],0,45),
 ('v270-shell',['python','tests/test_v270_shell_sources.py'],0,45),
 ('dependency-pins',['python','tools/dependency_compatibility.py'],0,30),
 ('lock-gate',['python','tools/dependency_release_gate.py'],4,30),
 ('application-build',['python','tools/run_app_builds.py'],4,40),
]
results=[]
for name,cmd,expected,timeout in COMMANDS:
 start=time.monotonic()
 try:
  p=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,timeout=timeout)
  code=p.returncode;out=p.stdout;err=p.stderr
 except subprocess.TimeoutExpired as e:
  code=124;out=str(e.stdout or '')[-15000:];err='TIMEOUT'
 elapsed=round(time.monotonic()-start,3)
 log=ROOT/f'evidence/batches/v280-regression-{name}.log'
 log.write_text('COMMAND '+json.dumps(cmd)+'\nEXIT_CODE '+str(code)+'\nSTDOUT\n'+out+'\nSTDERR\n'+err+'\n')
 results.append({'name':name,'argv':cmd,'exit_code':code,'expected_exit_code':expected,'matched_expected':code==expected,'duration_seconds':elapsed,'log':str(log.relative_to(ROOT)),'blocked_as_expected':expected==4 and code==4})
 print(name,code,'expected',expected,'MATCH' if code==expected else 'MISMATCH',elapsed,flush=True)
report={'version':'0.28.0','executed_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'checks':results,'matched_expected':sum(x['matched_expected'] for x in results),'clinical_release':False}
(ROOT/'evidence/batches/v280-regression-summary.json').write_text(json.dumps(report,indent=2)+'\n')
sys.exit(0 if all(x['matched_expected'] for x in results) else 1)
