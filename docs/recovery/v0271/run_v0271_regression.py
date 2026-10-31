#!/usr/bin/env python3
"""Bounded regression checks with actual exit codes; blocked gates remain blocked."""
from pathlib import Path
import datetime,json,subprocess,sys
r=Path(__file__).resolve().parents[1]
checks=[('java-security-scheduling',['bash','tools/check_java_security.sh'],0),('ux-tsc',['tsc','-p','packages/ux-core/tsconfig.json'],0),('ux-node',['node','--test',*map(str,sorted(Path('packages/ux-core/tests').glob('*.test.cjs')))],0),('native-tsx-source',['npm','run','check:source'],0),('baseline',['python','tests/test_baseline.py'],0),('navigation',['python','tests/test_navigation_contract.py'],0),('historical-v240',['python','tests/test_v240_checkpoint.py'],0),('v270-engineering',['python','tests/test_v270_engineering.py','-q'],0),('v270-shell',['python','tests/test_v270_shell_sources.py','-q'],0),('batch-ledger',['python','tests/test_batch_ledger.py'],0),('status-matrix',['python','tools/verify_status_matrix.py'],0),('scheduling-ddl',['python','tests/test_v0271_scheduling_ddl.py'],0),('sql-audit',['python','tools/audit_postgres_migrations.py'],0),('clinical-release-denied',['python','tools/verify_release_gate.py','--request-release'],3),('dependencies-blocked',['python','tools/dependency_release_gate.py'],4),('postgres-blocked',['python','tools/probe_postgres_readiness.py'],4),('app-build-blocked',['python','tools/run_app_builds.py'],4)]
rows=[]
for name,cmd,expected in checks:
 try:
  p=subprocess.run(cmd,cwd=r,text=True,capture_output=True,timeout=60);code=p.returncode;out=p.stdout[-9000:];err=p.stderr[-5000:]
 except subprocess.TimeoutExpired as e:code=124;out=str(e.stdout or '')[-8000:];err='TIMEOUT'
 except OSError as e:code=127;out='';err=str(e)
 row={'id':name,'cmd':cmd,'exit_code':code,'expected_code':expected,'matched_expected':code==expected,'scope':'BLOCKED/DENIED not successful execution' if expected else 'Source/unit/mock/static only'};rows.append(row)
 (r/f'evidence/batches/v0271-{name}.log').write_text('COMMAND '+repr(cmd)+'\nEXIT '+str(code)+' EXPECTED '+str(expected)+'\n'+out+'\n'+err)
 print(name,code,'expected',expected,flush=True)
report={'branch':'v0.27.1-recovery','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'checks':rows,'matched':sum(x['matched_expected'] for x in rows),'total':len(rows),'note':'Expected exit 3/4 means DENIED/BLOCKED, not clinical release, successful installation or build. Chromium static specimen separately verified.'}
(r/'evidence/batches/v0271-regression-summary.json').write_text(json.dumps(report,indent=2)+'\n')
sys.exit(0 if report['matched']==len(rows) else 1)
