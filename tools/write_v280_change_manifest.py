#!/usr/bin/env python3
import pathlib,json,hashlib,datetime
root=pathlib.Path(__file__).resolve().parents[1]
old=json.loads((root/'evidence/CUMULATIVE_FILE_SHA256.json').read_text())['files']
ignore={'evidence/CUMULATIVE_FILE_SHA256.json','evidence/v280-changed-files.json'}
now={}
for p in root.rglob('*'):
 if not p.is_file():continue
 rel=p.relative_to(root).as_posix()
 if rel in ignore or any(x in {'.git','__pycache__','node_modules','.next','.expo'} for x in p.parts) or p.suffix in {'.class','.pyc'}:continue
 now[rel]=hashlib.sha256(p.read_bytes()).hexdigest()
new=[{'path':k,'sha256':v} for k,v in sorted(now.items()) if k not in old]
changed=[{'path':k,'old_sha256':old[k],'new_sha256':v} for k,v in sorted(now.items()) if k in old and old[k]!=v]
removed=[k for k in sorted(old) if k not in now]
assert not removed,removed
result={'version':'0.28.0','baseline':'0.27.0','generated_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'inherited_files':len(old),'new_files':len(new),'modified_files':len(changed),'removed_files':removed,
        'new':new,'modified':changed,'clinical_release':False}
(root/'evidence/v280-changed-files.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS changed manifest',len(old),'inherited',len(new),'new',len(changed),'modified',len(removed),'removed')
