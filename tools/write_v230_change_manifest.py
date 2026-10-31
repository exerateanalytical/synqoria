from pathlib import Path
import json,hashlib,zipfile
root=Path(__file__).resolve().parents[1]
old=Path('/mnt/data/SYNQORIA_v0.22.0_cumulative.zip')
with zipfile.ZipFile(old) as z:
 previous=json.loads(z.read('evidence/CUMULATIVE_FILE_SHA256.json'))['files']
now={}
for p in root.rglob('*'):
 if not p.is_file():continue
 rel=p.relative_to(root).as_posix()
 if rel in {'evidence/CUMULATIVE_FILE_SHA256.json','evidence/v230-changed-files.json'}:continue
 if any(x in {'dist','node_modules','.next','.expo','__pycache__','.git'} for x in p.parts) or p.suffix in {'.class','.pyc'}:continue
 now[rel]=hashlib.sha256(p.read_bytes()).hexdigest()
added=sorted(set(now)-set(previous));changed=sorted(k for k in set(now)&set(previous) if now[k]!=previous[k]);deleted=sorted(set(previous)-set(now))
result={'source':'v0.22.0','target':'v0.23.0','added':[{ 'path':k,'sha256':now[k]} for k in added],
 'modified':[{ 'path':k,'before':previous[k],'after':now[k]} for k in changed],
 'deleted':deleted,'counts':{'added':len(added),'modified':len(changed),'deleted':len(deleted)}}
(root/'evidence/v230-changed-files.json').write_text(json.dumps(result,indent=2)+'\n')
print(result['counts'])
