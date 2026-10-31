"""Create verifiable complete cumulative archive; omit only build cache and itself."""
from pathlib import Path
import json,hashlib,zipfile,datetime
root=Path(__file__).resolve().parents[1]
version=json.loads((root/'registers/batch-ledger.json').read_text())['version']
out=Path(f'/mnt/data/SYNQORIA_v{version}_cumulative.zip')
manifest_path=root/'evidence/CUMULATIVE_FILE_SHA256.json'
ignore={'evidence/CUMULATIVE_FILE_SHA256.json'}
files=[]
for path in root.rglob('*'):
    if not path.is_file():continue
    relative=path.relative_to(root).as_posix()
    if relative in ignore or any(part in {'node_modules','.next','.expo','__pycache__','.git'} for part in path.parts):continue
    if path.suffix in {'.class','.pyc'}:continue
    files.append((relative,path))
files.sort()
manifest={'version':version,'created_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'files':{rel:hashlib.sha256(p.read_bytes()).hexdigest() for rel,p in files}}
manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
files.append(('evidence/CUMULATIVE_FILE_SHA256.json',manifest_path))
with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for rel,path in sorted(files):z.write(path,arcname=rel)
with zipfile.ZipFile(out) as z:
    assert z.testzip() is None
    for rel,digest in manifest['files'].items():
        assert hashlib.sha256(z.read(rel)).hexdigest()==digest,rel
sha=hashlib.sha256(out.read_bytes()).hexdigest()
Path(f'/mnt/data/SYNQORIA_v{version}_SHA256.txt').write_text(f'{sha}  {out.name}\n')
Path('/mnt/data/SYNQORIA_CURRENT.zip').write_bytes(out.read_bytes())
print(f'PASS: {len(files)} files in full cumulative archive; zip sha256 {sha}; size {out.stat().st_size} bytes')
