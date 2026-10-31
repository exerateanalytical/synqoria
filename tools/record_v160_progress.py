"""Execute each v0.16.0 batch separately and record only genuine successful tests."""
from pathlib import Path
import subprocess,json,datetime,hashlib,sys
R=Path(__file__).resolve().parents[1];E=R/'evidence/batches';E.mkdir(parents=True,exist_ok=True)
rows=[
('P05-B0022','Constrain provisional JDBC registration writer to package-local seam and expose only locked production admission','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/RegistrationProductionBoundary.java'),
('P05-B0023','Append-only review event state transitions reject reapproval, resurrection and version gaps','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/ReviewEventTransition.java'),
('P05-B0024','Domain-separated audit hash chain binds event kind, version and prior digest','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/ReviewAuditChain.java'),
('P05-B0025','Authoritative reviewer grant epoch/session freshness comparison with revocation denial','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/ReviewerGrantEpoch.java'),
('P05-B0026','Review-only readiness composition of independent IAM, revocation and database evidence','java_unit','services/clinical-core/src/main/java/org/synqoria/registration/ReviewReadinessReport.java'),
('P02-B0040','Canonical workforce intake deep-link allowlist with strict shell/role/route checks','ts_node_unit','packages/ux-core/src/registration-route-guard.ts'),
('P02-B0041','Non-identifying EN/FR accessibility error summary with deterministic order and no save','ts_node_unit','packages/ux-core/src/registration-a11y-summary.ts'),
('P02-B0042','Native intake lifecycle invalidates volatile synthetic drafts on suspend/offline','ts_node_unit','packages/ux-core/src/registration-mobile-lifecycle.ts'),
('P02-B0043','Native staff registration preview binds accessible review summary and visible lock','tsx_syntax_and_source_static','apps/staff-mobile/RegistrationPreview.tsx'),
('P01-B0032','Executable release gate refuses promotion while clinical screen acceptance and infrastructure evidence absent','python_execution','tools/verify_release_gate.py'),
]
for ident,scope,level,src in rows:
 if level=='java_unit':cmd=['java','-ea','-cp','/tmp/synqoria-security-classes','org.synqoria.registration.V160ReviewSafetyTest',ident]
 elif level=='ts_node_unit':cmd=['node','--test',f'--test-name-pattern={ident}','packages/ux-core/tests/v160-intake-safety.test.cjs']
 elif level=='tsx_syntax_and_source_static':cmd=['python','tests/test_v160_native_integration.py']
 else:cmd=['python','tests/test_v160_release_gate.py']
 try:
  p=subprocess.run(cmd,cwd=R,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=30)
  rc=p.returncode;out=p.stdout
 except Exception as e:rc=99;out=str(e)
 path=E/f'v160-{ident}.log';path.write_text('$ '+' '.join(cmd)+'\n'+out+'\nexit='+str(rc)+'\n')
 if rc!=0:raise RuntimeError(f'{ident} failed (not counted): {out[-1000:]}')
 print('PASS',ident)
ledger_path=R/'registers/batch-ledger.json';ledger=json.loads(ledger_path.read_text());ids={x['id'] for x in ledger['batches']}
assert not any(x[0] in ids for x in rows),'Duplicate batch IDs'
for ident,scope,level,src in rows:
 ledger['batches'].append({'id':ident,'status':'implemented_unit_or_static_tested_NOT_integrated','scope':scope,
 'evidence':[src,f'evidence/batches/v160-{ident}.log','evidence/batches/P01-P05-v160-ten-batches.json'],
 'verification_level':level,'clinical_screen_implementation':False,'independently_reviewed':False,
 'clinically_reviewed':False,'accepted':False,'external_certification':False})
ledger['version']='0.16.0';ledger['next_batch']='P05-B0027: PostgreSQL reviewer event atomic persistence + restricted RLS integration with real service credentials; P03-B0017: OIDC/JWKS trust verification; P02-B0044: compiled Expo/Next UI once dependencies available.'
ledger['release_status']='NOT_CERTIFIED';ledger_path.write_text(json.dumps(ledger,indent=2,ensure_ascii=False)+'\n')
phases_path=R/'registers/phases.json';phases=json.loads(phases_path.read_text());phases['version']='0.16.0';phases['checkpoint']='v0.16.0: registration JDBC API narrowed, review event chain, grant epoch, accessible locked intake and executable release gate; not integrated'
for phase in phases['phases']:
 phase['batches_completed']=sum(b['id'].startswith(phase['id']+'-') and not b['status'].startswith('blocked') for b in ledger['batches'])
 if phase['id'] in ('P01','P02','P05'):
  phase['status']='in_progress';phase['certification']='not_certified';phase.setdefault('evidence',[]).append('evidence/batches/P01-P05-v160-ten-batches.json')
phases_path.write_text(json.dumps(phases,indent=2,ensure_ascii=False)+'\n')
for file in ['screen-status-matrix.json','module-status-matrix.json','screen-routes.json']:
 p=R/'registers'/file;d=json.loads(p.read_text());d['version']='0.16.0';p.write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n')
block_path=R/'registers/blockers.json';d=json.loads(block_path.read_text());d['checkpoint']='v0.16.0';d['blockers'].append({'id':'BLK-V160-RELEASE','severity':'clinical_release','affects':['P01','P02','P03','P05','P19'],
 'issue':'Review state/event hash chain and IAM epoch are local unit implementations, not durable PostgreSQL or externally verified identity. Native screen is source-only; production registration remains locked.',
 'mitigation':'Provision PostgreSQL least-privilege roles and verified OIDC; run real isolation, transaction, mobile builds and clinical safety approvals before release.', 'status':'open'})
block_path.write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n')
for file in ['package.json','packages/ux-core/package.json']:
 p=R/file;d=json.loads(p.read_text());d['version']='0.16.0';p.write_text(json.dumps(d,indent=2)+'\n')
results=[{'batch':ident,'verified':True,'exit_code':0,'scope':scope,'verification_level':level,'source':src,'evidence_log':f'evidence/batches/v160-{ident}.log'} for ident,scope,level,src in rows]
(E/'P01-P05-v160-ten-batches.json').write_text(json.dumps({'checkpoint':'v0.16.0','base':'v0.15.0','batch_target':10,'batches_passed':10,'results':results,'clinical_screen_implementation':0,'clinical_release':'NOT_CERTIFIED','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat()},indent=2)+'\n')
print('PASS: 10 individual batches; updated ledger/phase/screen/module/blocker status')
