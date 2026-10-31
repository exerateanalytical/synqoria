#!/usr/bin/env python3
import pathlib,subprocess,datetime,json,sys
R=pathlib.Path(__file__).resolve().parents[1]
checks=[
 ('ux-typescript',['tsc','-p','packages/ux-core/tsconfig.json']),
 ('ux-node',['node','--test',*map(str,sorted(pathlib.Path('packages/ux-core/tests').glob('*.test.cjs')))]),
 ('java-full-regression',['bash','tools/check_java_security.sh']),
 ('native-and-tsx-source',['npm','run','check:source']),
 ('canonical-registers',['python','tests/test_baseline.py']),
 ('navigation-registers',['python','tests/test_navigation_contract.py']),
 ('historical-checkpoint',['python','tests/test_v240_checkpoint.py']),
 ('v260-foundation',['python','tests/test_v260_foundations.py','-v']),
 ('v270-engineering',['python','tests/test_v270_engineering.py','-v']),
 ('v270-shell',['python','tests/test_v270_shell_sources.py','-v']),
 ('v270-browser-more',['python','tests/test_v270_browser.py','P02-B0090']),
 ('v270-browser-viewport',['python','tests/test_v270_browser.py','P02-B0092']),
 ('contract-schema',['python','tests/test_contract.py']),
 ('sql-ddl-audit',['python','tools/audit_postgres_migrations.py']),
 ('v250-db-contract',['python','tests/test_v250_review_audit_ddl.py']),
 ('status-matrix',['python','tools/verify_status_matrix.py']),
 ('batch-ledger',['python','tests/test_batch_ledger.py']),
 ('clinical-release-denial',['python','tools/verify_release_gate.py','--request-release']),
 ('dependency-gate',['python','tools/dependency_release_gate.py']),
 ('postgres-preflight',['python','tools/probe_postgres_readiness.py']),
 ('app-build-attempt',['python','tools/run_app_builds.py']),
]
results=[];folder=R/'evidence/batches';folder.mkdir(exist_ok=True,parents=True)
for name,cmd in checks:
 try:
  p=subprocess.run(cmd,cwd=R,text=True,capture_output=True,timeout=150)
  code=p.returncode;stdout=p.stdout[-20000:];stderr=p.stderr[-12000:]
 except subprocess.TimeoutExpired as e:code=124;stdout=str(e.stdout or '')[-20000:];stderr='TIMEOUT'
 except OSError as e:code=127;stdout='';stderr=repr(e)
 expected=3 if name=='clinical-release-denial' else 4 if name in ('dependency-gate','postgres-preflight','app-build-attempt') else 0
 results.append({'name':name,'argv':cmd,'exit_code':code,'expected_code':expected,'expected':code==expected,'stdout':stdout,'stderr':stderr})
 (folder/f'v270-regression-{name}.log').write_text('COMMAND '+repr(cmd)+'\nEXIT '+str(code)+'\nEXPECTED '+str(expected)+'\nSTDOUT\n'+stdout+'\nSTDERR\n'+stderr)
 print(name,'exit',code,'expected',expected,flush=True)
payload={'version':'0.27.0','run_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'checks':results,'passed_expected':sum(x['expected'] for x in results),'total':len(results),'note':'expected-denial checks are BLOCKED/REJECTED, not positive release or build passes'}
(folder/'v270-regression-summary.json').write_text(json.dumps(payload,indent=2)+'\n')
print('MATCHED EXPECTED',payload['passed_expected'],'/',payload['total']);sys.exit(0 if payload['passed_expected']==payload['total'] else 1)
