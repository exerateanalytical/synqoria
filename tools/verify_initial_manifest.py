"""Verify original seed ZIP against its embedded SHA-256 ledger, not mutated worktree."""
from pathlib import Path
import json,hashlib,zipfile
root=Path(__file__).resolve().parents[1]
seed=root/'evidence/seed/SYNQORIA_Baseline_v0.1.0.zip'
with zipfile.ZipFile(seed) as z:
    manifest=json.loads(z.read('evidence/INITIAL_MANIFEST_SHA256.json'))
    for relative,digest in manifest.items():
        assert hashlib.sha256(z.read(relative)).hexdigest()==digest,relative
print(f'PASS: {len(manifest)} original seed ZIP entries match embedded SHA-256 manifest')
