from pathlib import Path
import subprocess,sys
r=Path(__file__).resolve().parents[1]
cmd=[sys.executable,str(r/'tools/verify_release_gate.py')]
allowed=subprocess.run(cmd+['--check-locked'],capture_output=True,text=True)
assert allowed.returncode==0,allowed.stderr
assert 'NO certification' in allowed.stdout
refused=subprocess.run(cmd+['--request-release'],capture_output=True,text=True)
assert refused.returncode==3,refused.stdout+refused.stderr
assert 'DENIED:' in refused.stderr
print('PASS P01-B0032 release gate denies promotion and validates canonical scope')
