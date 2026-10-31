"""Run 10 separately executed, meaningful implementation tests and preserve stdout/stderr/exit."""
import subprocess,json,datetime
from pathlib import Path
r=Path(__file__).resolve().parents[1]
e=r/'evidence/batches';e.mkdir(parents=True,exist_ok=True)
specs={
'P03-B0024':('Trusted operator-pinned RSA JWK and RFC7638 thumbprint checks','services/clinical-core/src/main/java/org/synqoria/security/OidcTrustedRsaJwk.java','java_crypto_unit'),
'P03-B0025':('JOSE protected-header allowlist rejects algorithm confusion, embedded keys and remote references','services/clinical-core/src/main/java/org/synqoria/security/OidcProtectedHeaderFence.java','java_unit'),
'P03-B0026':('Signed payload verification binds exact JWS header bytes to signature and trusted rotating key','services/clinical-core/src/main/java/org/synqoria/security/OidcSignedPayload.java','java_crypto_unit'),
'P03-B0027':('Trusted claim structural validation for exact issuer, audience, client, time and opaque IDs','services/clinical-core/src/main/java/org/synqoria/security/OidcTrustedClaimPolicy.java','java_unit'),
'P03-B0028':('PHI-free HMAC-pseudonymous security denial audit event format','services/clinical-core/src/main/java/org/synqoria/security/OidcSecurityAuditRecord.java','java_crypto_unit'),
'P02-B0048':('Static staff navigation allowlist prevents private IDs, unsafe paths and open redirects','packages/ux-core/src/safe-staff-route-intent.ts','typescript_node_unit'),
'P02-B0049':('Foreground generation reauthorization blocks stale, offline, revoked protected views','packages/ux-core/src/foreground-reauthorization.ts','typescript_node_unit'),
'P02-B0050':('EN/FR privacy-preserving push notification payloads omit patient/proxy data','packages/ux-core/src/privacy-notification.ts','typescript_node_unit'),
'P02-B0051':('Readonly bilingual four-stage registration journey integrated into native staff preview','packages/ux-core/src/locked-registration-journey.ts','typescript_node_unit_plus_native_source'),
'P02-B0052':('Four accessible disabled EN/FR bottom destinations integrated in both native shells','packages/ux-core/src/locked-navigation-accessibility.ts','typescript_node_unit_plus_native_source')}
results=[]
for batch,(scope,source,level) in specs.items():
 if batch.startswith('P03'):
  cmd=['java','-ea','-cp','/mnt/data/synqoria_v18/classes','org.synqoria.security.V180OidcBoundaryTest',batch]
 else:
  cmd=['node','--test',f'--test-name-pattern={batch}','packages/ux-core/tests/v180-foundations.test.cjs']
 p=subprocess.run(cmd,cwd=r,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=45)
 log=f'evidence/batches/v180-{batch}.log'
 (r/log).write_text('command: '+' '.join(cmd)+'\nexit='+str(p.returncode)+'\n'+p.stdout)
 passed=p.returncode==0 and ('PASS '+batch in p.stdout if batch.startswith('P03') else ('# pass 1' in p.stdout and '# fail 0' in p.stdout))
 results.append({'batch':batch,'scope':scope,'source':source,'verification_level':level,'verified':passed,'exit_code':p.returncode,'evidence_log':log})
 print(batch,'PASS' if passed else 'FAIL',p.returncode)
 if not passed:break
out={'checkpoint':'v0.18.0','base':'v0.17.0','batch_target':10,'batches_passed':sum(x['verified'] for x in results),'results':results,'created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'note':'Tests run independently; no production OIDC, PostgreSQL, Expo or clinical release implied.'}
(e/'P02-P03-v180-ten-batches.json').write_text(json.dumps(out,indent=2)+'\n')
if len(results)!=10 or not all(x['verified'] for x in results):raise SystemExit(1)
