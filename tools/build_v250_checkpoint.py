"""Build and independently verify full cumulative v0.25.0 ZIP and file SHA-256 manifest."""
from pathlib import Path
import zipfile,hashlib,json,datetime,os
root=Path(__file__).resolve().parents[1]
archive=Path('/mnt/data/SYNQORIA_v0.25.0_cumulative.zip')
source=Path('/mnt/data/SYNQORIA_v0.24.0_cumulative.zip')
old=zipfile.ZipFile(source)
old_hash={n:hashlib.sha256(old.read(n)).hexdigest() for n in old.namelist() if n!='evidence/CUMULATIVE_FILE_SHA256.json'}
for p in root.rglob('*.pyc'):p.unlink()
for p in sorted(root.rglob('__pycache__'),reverse=True):
 try:p.rmdir()
 except OSError:pass
for p in root.rglob('.DS_Store'):p.unlink()
def scan():
 return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file() and p.relative_to(root).as_posix()!='evidence/CUMULATIVE_FILE_SHA256.json'}
new=scan()
change={'checkpoint':'v0.25.0','source':'v0.24.0','added':sorted(set(new)-set(old_hash)),
        'changed':sorted(k for k in new.keys()&old_hash.keys() if new[k]!=old_hash[k]),
        'deleted':sorted(set(old_hash)-set(new)),
        'scope':'SHA-256 byte comparison against verified v0.24.0 cumulative ZIP'}
(root/'evidence/v250-changed-files.json').write_text(json.dumps(change,indent=2)+'\n')
new=scan()
manifest={'version':'0.25.0','created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'files':dict(sorted(new.items()))}
(root/'evidence/CUMULATIVE_FILE_SHA256.json').write_text(json.dumps(manifest,indent=2)+'\n')
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(root.rglob('*')):
  if p.is_file():z.write(p,p.relative_to(root).as_posix())
sha=hashlib.sha256(archive.read_bytes()).hexdigest()
Path('/mnt/data/SYNQORIA_v0.25.0_SHA256.txt').write_text(f'{sha}  {archive.name}\n')
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 m=json.loads(z.read('evidence/CUMULATIVE_FILE_SHA256.json'))
 assert len(m['files'])==len(z.namelist())-1
 for name,digest in m['files'].items():assert hashlib.sha256(z.read(name)).hexdigest()==digest,name
 for name,digest in old_hash.items():
  if name not in change['changed'] and name not in change['deleted']:
   assert m['files'][name]==digest,name
print(json.dumps({'zip':str(archive),'sha256':sha,'zip_entries':len(zipfile.ZipFile(archive).namelist()),
                  'manifest_entries':len(manifest['files']),'source_entries':len(old.namelist()),
                  'added':len(change['added']),'changed':len(change['changed']),'deleted':len(change['deleted']),
                  'bytes':archive.stat().st_size},indent=2))
