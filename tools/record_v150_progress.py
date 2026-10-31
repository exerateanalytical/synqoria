"""Record executed source-only v0.15.0 increments; never upgrade release state."""
from pathlib import Path
import subprocess, json,datetime,hashlib
R=Path(__file__).resolve().parents[1]
EV=R/'evidence/batches';EV.mkdir(exist_ok=True,parents=True)
JAVAC=['java','-ea','-cp','/tmp/synqoria-security-classes','org.synqoria.registration.V150ReviewGuardTest']
BATCHES=[
 ('P05-B0016','Bounded server-side reviewer session scope with distinct actor, policy epoch and revocation checks','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/VerifiedReviewerSession.java'),
 ('P05-B0017','Independent reviewer grant and candidate revision equality fence','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/ReviewerSeparationFence.java'),
 ('P05-B0018','Review decision revocation/freshness and stored-version gate','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/ReviewRevocationFence.java'),
 ('P05-B0019','Domain-separated review audit reference digest, no patient demographics','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/ReviewAuditBinding.java'),
 ('P05-B0020','Clinical registration write release remains locked regardless of caller-provided readiness flags','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/ReviewApprovalReleaseFence.java'),
 ('P05-B0021','Reviewer/candidate/decision freshness preflight; never grants mutation release','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/RegistrationReviewPreflight.java'),
 ('P02-B0037','Bilingual non-identifying duplicate-review status and no-save path','ts_node_unit','packages/ux-core/src/registration-duplicate-status.ts'),
 ('P02-B0038','Volatile draft invalidation on facility/actor/session switch and logout','ts_node_unit','packages/ux-core/src/registration-context-fence.ts'),
 ('P02-B0039','Accessible locked registration action and native preview binding','ts_node_and_source_static','packages/ux-core/src/registration-accessible-actions.ts'),
 ('P01-B0031','Quarantined append-only review-event DDL structure with default-deny RLS and no grant','sql_static_only','services/clinical-core/db/migration/V008__registration_review_event_quarantine.sql'),
]
rows=[]
for ident,scope,level,src in BATCHES:
 if level=='java_unit':cmd=JAVAC+[ident]
 elif level.startswith('ts_'):cmd=['node','--test',f'--test-name-pattern={ident}','packages/ux-core/tests/v150-mobile-review.test.cjs']
 else:cmd=['python','tests/test_v150_review_events.py']
 p=subprocess.run(cmd,cwd=R,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=20)
 (EV/f'v150-{ident}.log').write_text('$ '+' '.join(cmd)+'\n'+p.stdout+'\nexit='+str(p.returncode)+'\n')
 rows.append({'batch':ident,'verified':p.returncode==0,'exit_code':p.returncode,'verification_level':level,'source':src,'evidence_log':f'evidence/batches/v150-{ident}.log'})
 if p.returncode!=0:raise RuntimeError(f'{ident} failed: {p.stdout[-1000:]}')
ledger_path=R/'registers/batch-ledger.json';ledger=json.loads(ledger_path.read_text());ids={x['id'] for x in ledger['batches']}
if any(r['batch'] in ids for r in rows):raise RuntimeError('Duplicate batch ID detected')
for (ident,scope,level,src),row in zip(BATCHES,rows):
 ledger['batches'].append({'id':ident,'status':'implemented_unit_or_static_tested_NOT_integrated','scope':scope,
 'evidence':[src,row['evidence_log'],'evidence/batches/P01-P05-v150-ten-batches.json'],
 'verification_level':level,'clinical_screen_implementation':False,'independently_reviewed':False,
 'clinically_reviewed':False,'accepted':False,'external_certification':False})
ledger['version']='0.15.0';ledger['next_batch']='P05-B0022: independent reviewer IAM/PDP plus actual PostgreSQL RLS and audit/outbox transaction tests; P02-B0040: compile Next/Expo UI, then safely bind to verified authentication; P01-B0032: locked dependency build in CI';ledger['release_status']='NOT_CERTIFIED'
ledger_path.write_text(json.dumps(ledger,indent=2,ensure_ascii=False)+'\n')
phases_path=R/'registers/phases.json';phases=json.loads(phases_path.read_text());phases['version']='0.15.0';phases['checkpoint']='v0.15.0: reviewer freshness and audit guards, restricted event DDL and locked mobile review presentation; not integrated'
for phase in phases['phases']:
 phase['batches_completed']=len([b for b in ledger['batches'] if b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked')]);
 if phase['id'] in ['P01','P02','P05']:
  phase['status']='in_progress';phase['certification']='not_certified';phase.setdefault('evidence',[]).append('evidence/batches/P01-P05-v150-ten-batches.json')
phases_path.write_text(json.dumps(phases,indent=2,ensure_ascii=False)+'\n')
for file in ['screen-status-matrix.json','module-status-matrix.json','screen-routes.json']:
 p=R/'registers'/file;data=json.loads(p.read_text());data['version']='0.15.0';p.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
block_path=R/'registers/blockers.json';block=json.loads(block_path.read_text());block['checkpoint']='v0.15.0';block.setdefault('blockers',[]).append({'id':'BLK-V150-REVIEW','severity':'clinical_release','affects':['P03','P05','P19'], 'issue':'Reviewer session and event-chain models are unit/static tested only. No production IAM, approval service, PostgreSQL RLS role, audit record or release signature.', 'mitigation':'Obtain reviewer credential provider, human owner, least-privilege PostgreSQL environment, transactional approval/revocation audit, and independent clinical review before enabling registration.', 'status':'open'})
block_path.write_text(json.dumps(block,indent=2,ensure_ascii=False)+'\n')
for file in ['package.json','packages/ux-core/package.json']:
 p=R/file;data=json.loads(p.read_text());data['version']='0.15.0';p.write_text(json.dumps(data,indent=2)+'\n')
evidence={'checkpoint':'v0.15.0','base':'v0.14.0','batch_target':10,'batches_passed':sum(x['verified'] for x in rows),'results':rows,'clinical_screen_implementation':0,'clinical_release':'NOT_CERTIFIED','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat()}
(EV/'P01-P05-v150-ten-batches.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('PASS 10 individually invoked batch tests; ledger and phase counters updated')
