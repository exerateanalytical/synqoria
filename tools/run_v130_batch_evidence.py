"""Run ten independently selectable tests for real source increments; no clinical integration claims."""
from pathlib import Path
import subprocess,json,datetime
root=Path(__file__).resolve().parents[1]
java=['java','-ea','-cp','/mnt/data/synqoria_v013_test_classes','org.synqoria.registration.V130RegistrationSafetyTest']
node=['node','--test']
source=['python','tests/test_v130_registration_source.py']
work=[
 ('P05-B0006',[java+['P05-B0006']],['services/clinical-core/src/main/java/org/synqoria/registration/RegistrationFingerprint.java']),
 ('P05-B0007',[java+['P05-B0007']],['services/clinical-core/src/main/java/org/synqoria/registration/RegistrationCommandIssuer.java']),
 ('P05-B0008',[java+['P05-B0008']],['services/clinical-core/src/main/java/org/synqoria/registration/RegistrationReviewPermit.java']),
 ('P05-B0009',[java+['P05-B0009']],['services/clinical-core/src/main/java/org/synqoria/registration/RegistrationReplayDecision.java']),
 ('P05-B0010',[java+['P05-B0010'],source],['services/clinical-core/src/main/java/org/synqoria/registration/DuplicateCandidateQuery.java','services/clinical-core/db/migration/V006__identity_lookup_quarantine.sql']),
 ('P04-B0012',[java+['P04-B0012']],['services/clinical-core/src/main/java/org/synqoria/facility/ScopedDbTransaction.java']),
 ('P02-B0030',[node+['--test-name-pattern=P02-B0030','packages/ux-core/tests/v130-intake.test.cjs']],['packages/ux-core/src/registration-draft-session.ts']),
 ('P02-B0031',[node+['--test-name-pattern=P02-B0031','packages/ux-core/tests/v130-intake.test.cjs'],source],['packages/ux-core/src/registration-error-copy.ts','apps/web/app/workforce/patients/new/page.tsx']),
 ('P02-B0032',[node+['--test-name-pattern=P02-B0032','packages/ux-core/tests/v130-intake.test.cjs'],source],['packages/ux-core/src/registration-native-model.ts','apps/staff-mobile/RegistrationPreview.tsx']),
 ('P01-B0029',[['python','tests/test_v130_registration_contract.py']],['contracts/openapi/synqoria-registration-draft.yaml'])
]
results=[]
for ident,commands,files in work:
 evidence=[]
 for cmd in commands:
  try:
   p=subprocess.run(cmd,cwd=root,capture_output=True,text=True,timeout=60)
   evidence.append({'command':' '.join(cmd),'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr})
  except subprocess.TimeoutExpired as e:
   evidence.append({'command':' '.join(cmd),'exit_code':None,'timeout':True,'stdout':str(e.stdout),'stderr':str(e.stderr)})
 ok=all(x['exit_code']==0 for x in evidence)
 results.append({'batch':ident,'verified':ok,'exit_code':0 if ok else 1,'commands':evidence,'source_files':files,'not_clinically_certified':True})
 print(ident,'PASS' if ok else 'FAIL')
report={'recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'batch_target':10,'batches_passed':sum(x['verified'] for x in results),'results':results,'limitations':'Java unit/mock JDBC, TypeScript/Node unit, static YAML/SQL/TSX only; no live PostgreSQL/OIDC/Expo/Next build or clinical certification.'}
(root/'evidence/batches/P01-P05-v130-ten-batches.json').write_text(json.dumps(report,indent=2)+'\n')
if report['batches_passed']!=10:raise SystemExit(1)
