from pathlib import Path
import json,datetime,subprocess,re,hashlib
r=Path(__file__).resolve().parents[1];e=r/'evidence/batches'
now=datetime.datetime.now(datetime.timezone.utc).isoformat()
load=lambda p:json.loads((r/p).read_text())
save=lambda p,x:(r/p).write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n')
items=[
 ('P05-B0028','Scoped reviewer/registrar separation and exact tenant/facility/command/revision fence','services/clinical-core/src/main/java/org/synqoria/registration/ReviewScopeFence.java','V190ReviewScopeTest'),
 ('P05-B0029','Idempotency fingerprint and wrong-scope conflict classification; durable atomicity not yet present','services/clinical-core/src/main/java/org/synqoria/registration/ReviewIdempotencyFence.java','V190IdempotencyTest'),
 ('P05-B0030','PHI-free review decision outbox descriptor and stable partition routing','services/clinical-core/src/main/java/org/synqoria/registration/ReviewOutboxDescriptor.java','V190OutboxDescriptorTest'),
 ('P05-B0031','Digest chain verification with strictly legal approval/revocation state sequence','services/clinical-core/src/main/java/org/synqoria/registration/ReviewChainVerifier.java','V190ChainVerifierTest'),
 ('P05-B0032','Reviewer evidence freshness and expiry fence with bounded clock skew','services/clinical-core/src/main/java/org/synqoria/registration/ReviewFreshnessWindow.java','V190FreshnessTest'),
 ('P02-B0053','Critical-field text wrapping and accessibility sizing contract','packages/ux-core/src/native-text-safety.ts','P02-B0053'),
 ('P02-B0054','Fail-closed facility switch barrier with sensitive-state disposal and generation check','packages/ux-core/src/facility-switch-barrier.ts','P02-B0054'),
 ('P02-B0055','Patient/proxy read-only disclosure guard requiring matching grant scope, expiry and facility','packages/ux-core/src/proxy-relationship-fence.ts','P02-B0055'),
 ('P02-B0056','Exact authorized stable root-only navigation fence; excludes private details and redirects','packages/ux-core/src/safe-clinical-route-root.ts','P02-B0056'),
 ('P02-B0057','Bilingual registration release-denial presentation wired into staff native preview','packages/ux-core/src/registration-release-presentation.ts','P02-B0057')]
results=[]
for bid,scope,src,command in items:
 assert (r/src).is_file()
 if bid.startswith('P05'):
  cmd=['java','-ea','-cp',str(r/'classes'),'org.synqoria.registration.'+command]
 else:
  cmd=['node','--test','--test-name-pattern='+command,'packages/ux-core/tests/v190-foundations.test.cjs']
 p=subprocess.run(cmd,cwd=r,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=35)
 assert p.returncode==0,(bid,p.stdout)
 if bid.startswith('P02'):assert re.search(r'# pass 1\s',p.stdout), (bid,p.stdout)
 else:assert 'PASS '+bid in p.stdout
 log=f'evidence/batches/v190-{bid}.log';(r/log).write_text('$ '+' '.join(cmd)+'\n'+p.stdout+'\nexit=0 '+bid+'\n')
 results.append({'batch':bid,'scope':scope,'source':src,'verification_level':'java_unit' if bid.startswith('P05') else 'typescript_node_unit_plus_native_source' if bid=='P02-B0057' else 'typescript_node_unit','verified':True,'exit_code':0,'evidence_log':log})
ledger=load('registers/batch-ledger.json');assert ledger['version']=='0.18.0';ids={x['id'] for x in ledger['batches']};assert not any(x['batch'] in ids for x in results)
for x in results:
 ledger['batches'].append({'id':x['batch'],'status':'implemented_unit_or_static_tested_NOT_integrated','scope':x['scope'],'evidence':[x['source'],x['evidence_log']],'verification_level':x['verification_level'],'clinical_screen_implementation':False,'independently_reviewed':False,'clinically_reviewed':False,'accepted':False,'external_certification':False})
ledger['version']='0.19.0';ledger['next_batch']='P05-B0027 BLOCKED: PostgreSQL reviewer transaction with restricted-role RLS and real concurrency tests; next independently unblocked P02-B0058 native safe-area/keyboard layout and accessibility test, or P03-B0029 vetted JOSE parser when dependencies available.'
save('registers/batch-ledger.json',ledger)
ph=load('registers/phases.json');ph['version']='0.19.0';ph['checkpoint']='v0.19.0: 5 reviewer security/reliability precondition components + 5 UX/privacy guards; no database/clinical/native builds'
for p in ph['phases']:
 p['batches_completed']=sum(b['id'].startswith(p['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
 if p['id'] in ['P02','P05']:
  p['evidence'].append('evidence/batches/P02-P05-v190-ten-batches.json')
 assert p['certification']!='certified'
save('registers/phases.json',ph)
for name in ['module-status-matrix.json','screen-status-matrix.json','screen-routes.json']:
 d=load('registers/'+name);d['version']='0.19.0';save('registers/'+name,d)
bl=load('registers/blockers.json');bl['checkpoint']='v0.19.0';bl['blockers'].append({'id':'BLK-V190-REVIEW-PERSISTENCE','severity':'clinical_release','affects':['P05-B0027','P05-B0028','P05-B0029','P05-B0030','P05-B0031','P05-B0032'],'issue':'v0.19.0 reviewer fences and audit/outbox metadata are pure Java unit components; no actual PostgreSQL transaction, RLS, immutable audit custody, outbox persistence or hospital-approved reviewer identity. They MUST NOT be used as authorization to submit registration.','mitigation':'Provision PostgreSQL with restricted DB roles, verify migrations and RLS cross-tenant tests, wire audited transactional reviewer decision and outbox, obtain independent security/clinical review.','status':'open'})
save('registers/blockers.json',bl)
save('evidence/batches/P02-P05-v190-ten-batches.json',{'checkpoint':'v0.19.0','base':'v0.18.0','batch_target':10,'batches_passed':len(results),'results':results,'created_utc':now,'note':'Five Java and five TypeScript components; individually executed tests. No production PostgreSQL, OIDC, Next or Expo builds; no clinical certification.'})
pkg=load('package.json');pkg['version']='0.19.0';pkg['scripts']['check:registers']='python tests/test_baseline.py && python tests/test_navigation_contract.py && python tests/test_v190_checkpoint.py';save('package.json',pkg)
# legacy checkpoint verification must allow future versions without rewriting historical evidence
p=r/'tests/test_v180_checkpoint.py';s=p.read_text().replace("assert l['version']==p['version']=='0.18.0'","assert l['version']==p['version'] and tuple(map(int,l['version'].split('.'))) >= (0,18,0)").replace("assert x['version']=='0.18.0' and len(x['items'])==count","assert x['version']==l['version'] and len(x['items'])==count");p.write_text(s)
status='''# SYNQORIA — Engineering checkpoint v0.19.0

**Source:** v0.18.0 cumulative ZIP SHA-256 `5c0d815c985be9996d97763b6db2cbf6ea24d5dba3dcc5278bc3cef58011f40c`, all 494 internal manifest entries and 19 baseline seed entries verified before changes.

**Implemented this checkpoint:** 10 individually tested, nonclinical foundation increments: P05-B0028–P05-B0032 (review scope, idempotency conflict, PHI-free outbox descriptor, event-chain verifier, freshness) and P02-B0053–P02-B0057 (critical text policy, facility switch, proxy disclosure, static route allowlist, bilingual locked registration presentation). Staff RN source uses new locked status; no actual native build.

**Evidence:** `evidence/batches/P02-P05-v190-ten-batches.json` and per-batch execution logs; `evidence/batches/v190-*.log`. Full test outcomes are recorded separately. Tests use synthetic data.

**Safety:** The review components are NOT connected to a transactional PostgreSQL authority, RLS, clinical API or trusted IdP. The UX components are NOT backend access controls. Registration remains disabled, release NOT_CERTIFIED. 40 modules and 261 screen IDs preserved, 0/261 clinical screens implemented/accepted. No phase certified.

**Blockers:** PostgreSQL service and isolation/concurrency tests; vetted OIDC/JWKS and reviewer grant; native Next.js/Expo builds and device testing; independent clinical/security sign-offs; verified SYNQORIA GitHub repo. See `registers/blockers.json`.

**Next:** P05-B0027 (blocked: durable reviewer approval/revocation with RLS/audit/outbox and concurrency); independently unblocked P02-B0058 (native safe area, keyboard avoidance and 200% font checks). No external release authority claimed.
'''
(r/'docs/program/STATUS.md').write_text(status)
(r/'docs/program/V019_REVIEW_AND_ACCESSIBILITY.md').write_text('''# v0.19.0 review: what was and was not validated

Reviewer fences are pure Java deterministic checks and do not grant permission. Event-chain verifier validates both SHA-256 linkage and legal event transitions but does not prove external immutable custody or prevent a privileged rewrite of all events. Idempotency check needs a database unique constraint and atomic transaction; in-memory equality cannot prevent races. Outbox descriptor intentionally excludes patient identifiers and clinical payloads. Reviewer freshness checks require trusted clocks and authoritative revocation state.

UX guards are supplementary, not backend authorization. Native source contains a new bilingual disabled registration reason, but Expo, iOS/Android accessibility, safe areas and keyboard behavior have not been built/tested. Native text safety uses platform font scaling, no fixed line height or truncation for critical fields; this is a unit-tested design contract, not a certified drug administration screen.

**Release:** no registration POST, clinical approval, patient/proxy disclosure or live patient data is authorized by these components. **All clinical gates remain closed.**
''')
print('PASS ten individual batch commands and registers recorded',len(results))
