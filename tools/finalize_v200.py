#!/usr/bin/env python3
"""Full cumulative manifest + delta audit. Excludes generated manifests and transient compiler outputs."""
from pathlib import Path
import hashlib,json,datetime
r=Path(__file__).resolve().parents[1]
old=json.loads((r/'evidence/CUMULATIVE_FILE_SHA256.json').read_text())
assert old['version']=='0.19.0'
ignore={'evidence/CUMULATIVE_FILE_SHA256.json','evidence/batches/v200-changed-file-manifest.json'}
files={}
for p in r.rglob('*'):
 if not p.is_file():continue
 rel=p.relative_to(r).as_posix()
 if rel in ignore or any(x in {'.git','dist','node_modules','.next','.expo','__pycache__','classes'} for x in p.relative_to(r).parts) or p.suffix in {'.class','.pyc'}:continue
 files[rel]=hashlib.sha256(p.read_bytes()).hexdigest()
prev=old['files'];removed=sorted(set(prev)-set(files));assert not removed,removed
changed={k:files[k] for k in sorted(files) if k not in prev or files[k]!=prev[k]}
manifest={'from':'v0.19.0','to':'v0.20.0','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'added':sorted(set(files)-set(prev)),'modified':sorted(k for k in files.keys()&prev.keys() if files[k]!=prev[k]),'removed':removed,'changed_sha256':changed}
(r/'evidence/batches/v200-changed-file-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(r/'evidence/CUMULATIVE_FILE_SHA256.json').write_text(json.dumps({'version':'0.20.0','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'files':dict(sorted(files.items()))},indent=2)+'\n')
print('PASS',len(files),'manifest entries',len(manifest['added']),'added',len(manifest['modified']),'modified, 0 removed')
