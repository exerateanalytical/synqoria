from pathlib import Path
import hashlib,json,datetime
r=Path(__file__).resolve().parents[1]
previous=json.loads((r/'evidence/CUMULATIVE_FILE_SHA256.json').read_text());assert previous['version']=='0.17.0'
ignore={'evidence/CUMULATIVE_FILE_SHA256.json','evidence/batches/v180-changed-file-manifest.json'}
current={}
for p in r.rglob('*'):
 if not p.is_file():continue
 rel=p.relative_to(r).as_posix()
 if rel in ignore or any(part in {'.git','dist','node_modules','.next','.expo','__pycache__','classes'} for part in p.relative_to(r).parts) or p.suffix in {'.class','.pyc'}:continue
 current[rel]=hashlib.sha256(p.read_bytes()).hexdigest()
old=previous['files'];out={'from':'v0.17.0','to':'v0.18.0','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
'added':sorted(set(current)-set(old)),'modified':sorted(x for x in set(current)&set(old) if current[x]!=old[x]),'removed':sorted(set(old)-set(current)),
'changed_sha256':{k:current[k] for k in sorted(current) if k not in old or old[k]!=current[k]}}
(r/'evidence/batches/v180-changed-file-manifest.json').write_text(json.dumps(out,indent=2)+'\n')
assert not out['removed'],out['removed']
print('PASS changed-file manifest:',len(out['added']),'added,',len(out['modified']),'modified,',len(out['removed']),'removed')
