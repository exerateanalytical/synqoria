"""P01-B0044: review-only lock candidate and fail-closed actual-build CI."""
from pathlib import Path
import yaml
root=Path(__file__).resolve().parents[1]
proposal=(root/'.github/workflows/dependency-lock-candidate.yml').read_text()
ci=(root/'.github/workflows/engineering-checks.yml').read_text()
wf=yaml.safe_load(proposal)
assert 'workflow_dispatch' in wf.get('on',wf.get(True,{}))
assert wf['permissions']=={'contents':'read'}
steps=wf['jobs']['propose-lock']['steps']
commands='\n'.join(str(s.get('run','')) for s in steps)
assert 'npm install --package-lock-only --ignore-scripts' in commands
assert 'tools/verify_lock_integrity.py' in commands
assert 'tools/dependency_release_gate.py' in commands
assert 'npm ci' not in commands
assert 'git push' not in proposal and 'git commit' not in proposal
assert 'upload-artifact' in proposal
assert "cache: 'npm'" not in ci, 'missing lock must not break setup-node before prerequisite gate'
assert 'python3 tools/dependency_release_gate.py' in ci
assert 'npm ci --ignore-scripts' in ci
assert 'v280-actual-build-attempt.json' in ci
print('PASS P01-B0044: manual candidate, no unreviewed lock adoption, CI fail-closed')
