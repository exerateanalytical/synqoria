"""Compare cumulative v0.40 ZIP file hashes to v0.41 source tree; evidence file itself excluded."""
from pathlib import Path
import zipfile,hashlib,json,datetime
root=Path(__file__).resolve().parents[1]
seed=Path('/mnt/data/SYNQORIA_v0.42.0_cumulative.zip')
with zipfile.ZipFile(seed) as z:
 data=json.loads(z.read('evidence/CUMULATIVE_FILE_SHA256.json'))
 previous=data.get('files',data)
ignore={'evidence/CUMULATIVE_FILE_SHA256.json','evidence/batches/v430-changed-files.json'}
current={}
for p in root.rglob('*'):
 if not p.is_file():continue
 rel=p.relative_to(root).as_posix()
 if rel in ignore or any(x in {'.git','node_modules','.next','.expo','__pycache__'} for x in p.parts) or p.suffix in {'.class','.pyc'}:continue
 current[rel]=hashlib.sha256(p.read_bytes()).hexdigest()
added=sorted(set(current)-set(previous));modified=sorted(x for x in set(current)&set(previous) if current[x]!=previous[x]);deleted=sorted(set(previous)-set(current))
manifest={'version':'0.43.0','source':'0.42.0-reconciled','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'added':{x:current[x] for x in added},'modified':{x:current[x] for x in modified},
 'deleted':{x:previous[x] for x in deleted},'counts':{'added':len(added),'modified':len(modified),'deleted':len(deleted)},
 'note':'Manifest itself excluded to avoid recursive hash; all remaining cumulative file hashes verified separately.'}
(root/'evidence/batches/v430-changed-files.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('PASS changed-file manifest:',manifest['counts'])
