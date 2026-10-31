"""Execute available offline checks; preserve stdout, stderr and honest test scope."""
from pathlib import Path
import subprocess,json,datetime,os,re
root=Path(__file__).resolve().parents[1]
checks=[
 ('seed-manifest',['python','tools/verify_initial_manifest.py']),
 ('canonical-register',['python','tests/test_baseline.py']),
 ('route-integrity',['python','tests/test_navigation_contract.py']),
 ('ux-typescript-node',['npm','run','check:ux']),
 ('tsx-syntax-only',['node','tests/test_tsx_syntax.cjs']),
 ('web-shell-source',['python','tests/test_shell_sources.py']),
 ('source-style',['python','tests/test_source_safety.py']),
 ('locked-native-tabs-source',['python','tests/test_locked_mobile_shells.py']),
 ('openapi-parse',['python','tests/test_contract.py']),
 ('postgresql-sql-static',['python','tests/test_sql_scope_foundation.py']),
 ('postgresql-outbox-sql-static',['python','tests/test_sql_outbox.py']),
 ('postgresql-identity-sql-static',['python','tests/test_sql_identity.py']),
 ('v100-audit-schema-static',['python','tests/test_v100_audit_schema.py']),
 ('jdbc-schema-alignment-static',['python','tests/test_v090_jdbc_schema.py']),
 ('event-envelope-schema',['python','tests/test_event_envelope.py']),
 ('clinical-status-matrix',['python','tools/verify_status_matrix.py']),
 ('batch-evidence-ledger',['python','tests/test_batch_ledger.py']),
 ('v090-ten-batch-evidence',['python','tests/test_v090_batches.py']),
 ('v010-ten-batch-evidence',['python','tests/test_v100_batches.py']),
 ('parallel-checkpoint-merge',['python','tests/test_merge_ledger.py']),
 ('java-domain-policy',['bash','tools/check_java_security.sh']),
 ('loopback-http-contract',['python','tests/test_loopback_http_contract.py']),
 ('v110-mobile-source',['python','tests/test_v110_mobile_source.py']),
 ('v110-ten-batch-register',['python','tests/test_v110_batches.py']),
 ('chromium-legacy-static-preview',['python','tests/test_preview_browser.py']),
 ('chromium-bilingual-static-specimen',['python','tests/test_i18n_preview_browser.py']),
 ('v120-registration-static-sql-web',['python','tests/test_v120_registration_source.py']),
 ('v120-ten-batch-evidence',['python','tests/test_v120_batches.py']),
 ('v130-ten-batch-evidence',['python','tests/test_v130_batches.py']),
 ('v130-registration-contract',['python','tests/test_v130_registration_contract.py']),
 ('v130-registration-source',['python','tests/test_v130_registration_source.py']),
]
results=[]
for label,cmd in checks:
    try:
        completed=subprocess.run(cmd,cwd=root,capture_output=True,text=True,timeout=120)
        result={'id':label,'command':' '.join(cmd),'exit_code':completed.returncode,'stdout':completed.stdout,'stderr':completed.stderr}
    except subprocess.TimeoutExpired as e:
        result={'id':label,'command':' '.join(cmd),'exit_code':None,'timeout':True,'stdout':str(e.stdout),'stderr':str(e.stderr)}
    results.append(result)
    print(f'{label}: exit {result["exit_code"]}')
report={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'scope':'unit/domain/independent in-memory prototypes/draft SQL-static/schema/source/syntax and standalone static Chromium specimens ONLY; real loopback HTTP with injected TEST identity verifier but NO real OIDC, PostgreSQL, Next.js or Expo integration',
        'results':results,'all_passed':all(x['exit_code']==0 for x in results)}
tag=os.environ.get('SYNQORIA_EVIDENCE_TAG','v090')
if not re.fullmatch(r'v[0-9]{3}',tag):raise SystemExit('Unsafe evidence tag')
(root/f'evidence/batches/{"P01-P05" if tag in ("v120","v130") else "P01-P04"}-{tag}-checks.json').write_text(json.dumps(report,indent=2)+'\n')
if not report['all_passed']:raise SystemExit(1)
