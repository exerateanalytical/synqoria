#!/usr/bin/env python3
"""Execute each of ten distinct checks, recording actual exit codes/stdout; never auto-accept failures."""
from pathlib import Path
import subprocess,json,datetime
r=Path(__file__).resolve().parents[1]
out=r/'evidence/batches';out.mkdir(parents=True,exist_ok=True)
java=['java','-ea','-cp','/mnt/data/synqoria_v20_build/classes','org.synqoria.security.V200OidcFoundationsTest']
node=['node','--test']
checks=[
 ('P02-B0058',['python','tests/test_v200_native_sources.py','P02-B0058'],'native_safe_area_keyboard_source'),
 ('P02-B0059',node+['--test-name-pattern=P02-B0059','packages/ux-core/tests/v200-native-safety.test.cjs'],'native_dynamic_type_unit'),
 ('P02-B0060',node+['--test-name-pattern=P02-B0060','packages/ux-core/tests/v200-native-safety.test.cjs'],'native_privacy_latch_unit'),
 ('P02-B0061',['python','tests/test_v200_native_sources.py','P02-B0061'],'native_accessible_disabled_button_source'),
 ('P02-B0062',node+['--test-name-pattern=P02-B0062','packages/ux-core/tests/v200-native-safety.test.cjs'],'mobile_scope_disclosure_unit'),
 *[(f'P03-B00{i}',java+[f'P03-B00{i}'],kind) for i,kind in [(29,'issuer_discovery_fence'),(30,'pinned_keyset_snapshot'),(31,'bounded_token_replay_fence'),(32,'facility_shift_session_fence'),(33,'security_burst_fence')]]
]
results=[]
for batch,cmd,kind in checks:
 try:
  p=subprocess.run(cmd,cwd=r,capture_output=True,text=True,timeout=25)
  status=p.returncode;stdout=p.stdout;stderr=p.stderr
 except subprocess.TimeoutExpired as e:
  status=124;stdout=(e.stdout or b'').decode() if isinstance(e.stdout,bytes) else (e.stdout or '');stderr='TIMEOUT '+str(e)
 log=f'evidence/batches/v200-{batch}.log'
 (r/log).write_text(f'command={json.dumps(cmd)}\nexit={status} {batch}\nSTDOUT:\n{stdout}\nSTDERR:\n{stderr}\n')
 passed=status==0 and (('PASS '+batch in stdout) if batch.startswith('P03') else ('# pass 1' in stdout if batch in ('P02-B0059','P02-B0060','P02-B0062') else 'PASS '+batch in stdout))
 results.append(dict(batch=batch,kind=kind,command=cmd,exit_code=status,verified=passed,evidence_log=log,verification_level='java_unit' if batch.startswith('P03') else 'typescript_node_unit' if batch in ('P02-B0059','P02-B0060','P02-B0062') else 'native_source_static',clinical_screen_implementation=False,accepted=False))
 print(batch,'PASS' if passed else 'FAIL',status,flush=True)
report=dict(checkpoint='v0.20.0',base='v0.19.0',batch_target=10,batches_passed=sum(x['verified'] for x in results),results=results,generated_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),release='NOT_CERTIFIED')
(out/'P02-P03-v200-ten-batches.json').write_text(json.dumps(report,indent=2)+'\n')
if report['batches_passed']!=10:raise SystemExit('Only '+str(report['batches_passed'])+' batches verified')
