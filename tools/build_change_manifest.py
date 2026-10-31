"""Compare current working source to last verified cumulative source manifest."""
from pathlib import Path
import hashlib,json,datetime
root=Path(__file__).resolve().parents[1]
previous=json.loads((root/'evidence/CUMULATIVE_FILE_SHA256.json').read_text())
assert previous['version']=='0.4.0'
ignore={'evidence/CUMULATIVE_FILE_SHA256.json','evidence/batches/P01-P04-v050-file-changes.json'}
current={}
for path in root.rglob('*'):
 if not path.is_file():continue
 rel=path.relative_to(root).as_posix()
 if rel in ignore or any(p in {'dist','node_modules','.next','.expo','__pycache__','.git'} for p in path.parts) or path.suffix in {'.class','.pyc'}:continue
 current[rel]=hashlib.sha256(path.read_bytes()).hexdigest()
old=previous['files']
report={'from':'0.4.0','to':'0.5.0','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'added':sorted(set(current)-set(old)),
 'modified':sorted(p for p in set(current)&set(old) if current[p]!=old[p]),
 'removed':sorted(set(old)-set(current)),
 'source_sha256':{p:current[p] for p in sorted(current) if p not in old or current[p]!=old[p]}}
(root/'evidence/batches/P01-P04-v050-file-changes.json').write_text(json.dumps(report,indent=2)+'\n')
print(f"PASS: {len(report['added'])} added, {len(report['modified'])} modified, {len(report['removed'])} removed compared with v0.4.0")
