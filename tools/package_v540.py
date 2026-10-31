#!/usr/bin/env python3
"""Package complete v0.54.0 cumulative checkpoint with two noncyclic SHA-256 ledgers."""
from pathlib import Path
import hashlib,json,zipfile,sys
R=Path(__file__).resolve().parents[1]
OUT=Path('/mnt/data/SYNQORIA_v0.54.0_60BATCH_cumulative.zip')
MANIFEST=Path('evidence/CUMULATIVE_FILE_SHA256.json')
ROOT_MANIFEST=Path('manifest.sha256.json')

def sha(data):return hashlib.sha256(data).hexdigest()
def paths():
 return sorted((f for f in R.rglob('*') if f.is_file() and not any(x in f.parts for x in ('__pycache__','.git','node_modules')) and f.suffix not in ('.pyc','.class')),
  key=lambda x:x.relative_to(R).as_posix())

def package():
 current={f.relative_to(R).as_posix():sha(f.read_bytes()) for f in paths() if f.relative_to(R) not in (MANIFEST,ROOT_MANIFEST)}
 (R/ROOT_MANIFEST).write_text(json.dumps({'algorithm':'sha256','checkpoint':'v0.54.0',
  'scope':'all cumulative files except both manifest files; canonical manifest below binds this root manifest',
  'files':current},indent=2)+'\n')
 canonical={f.relative_to(R).as_posix():sha(f.read_bytes()) for f in paths() if f.relative_to(R)!=MANIFEST}
 (R/MANIFEST).write_text(json.dumps({'version':'0.54.0','checkpoint':'v0.54.0',
  'manifest_scope':'every ZIP entry except this canonical manifest','files':canonical},indent=2)+'\n')
 members=paths()
 with zipfile.ZipFile(OUT,'w',zipfile.ZIP_DEFLATED,compresslevel=8,allowZip64=True) as z:
  for f in members:
   arc=f.relative_to(R).as_posix()
   z.write(f,arc)
 with zipfile.ZipFile(OUT) as z:
  names=z.namelist()
  assert len(names)==len(set(names))
  assert set(names)==set(canonical)|{MANIFEST.as_posix()}
  for name,digest in canonical.items():
   assert sha(z.read(name))==digest, name
  nested=json.loads(z.read(ROOT_MANIFEST.as_posix()))
  assert set(nested['files'])==set(names)-{MANIFEST.as_posix(),ROOT_MANIFEST.as_posix()}
  for name,digest in nested['files'].items():assert sha(z.read(name))==digest
 digest=sha(OUT.read_bytes())
 sha_file=Path('/mnt/data/SYNQORIA_v0.54.0_SHA256.txt')
 sha_file.write_text(f'{digest}  {OUT.name}\n')
 print(json.dumps({'archive':str(OUT),'sha256':digest,'files':len(names),'canonical_hashes':len(canonical),'root_hashes':len(current),'bytes':OUT.stat().st_size},indent=2))
if __name__=='__main__':package()
