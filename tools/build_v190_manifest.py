from pathlib import Path
import hashlib,json,datetime
r=Path(__file__).resolve().parents[1]
old=json.loads((r/'evidence/CUMULATIVE_FILE_SHA256.json').read_text());assert old['version']=='0.18.0'
ignore={'evidence/CUMULATIVE_FILE_SHA256.json','evidence/batches/v190-changed-file-manifest.json'}
cur={}
for p in r.rglob('*'):
 if not p.is_file():continue
 rel=p.relative_to(r).as_posix()
 if rel in ignore or any(part in {'.git','dist','node_modules','.next','.expo','__pycache__','classes'} for part in p.relative_to(r).parts) or p.suffix in {'.class','.pyc'}:continue
 cur[rel]=hashlib.sha256(p.read_bytes()).hexdigest()
prev=old['files']
change={'from':'v0.18.0','to':'v0.19.0','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
'added':sorted(set(cur)-set(prev)),'modified':sorted(k for k in set(cur)&set(prev) if cur[k]!=prev[k]),'removed':sorted(set(prev)-set(cur)),
'changed_sha256':{k:cur[k] for k in sorted(cur) if k not in prev or cur[k]!=prev[k]}}
assert not change['removed'],change['removed']
(r/'evidence/batches/v190-changed-file-manifest.json').write_text(json.dumps(change,indent=2)+'\n')
print('PASS',len(change['added']),'added',len(change['modified']),'modified',len(change['removed']),'removed')
