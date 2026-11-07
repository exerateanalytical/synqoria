#!/usr/bin/env python3
"""Verify predecessor and emit byte-content-manifest-verified full cumulative archive. No clinical certification."""
from pathlib import Path
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
PARENT=Path('/mnt/data/SYNQORIA_v0.61.0_P07_10BATCH_cumulative.zip')
DEST=Path('/mnt/data/SYNQORIA_v0.62.0_P07_23BATCH_cumulative.zip')
PROOF=Path('/mnt/data/SYNQORIA_v0.62.0_PACKAGE_VERIFICATION.json')
SUM=Path('/mnt/data/SYNQORIA_v0.62.0_SHA256.txt')
assert PARENT.is_file()
with zipfile.ZipFile(PARENT) as z:
 old=set(x for x in z.namelist() if not x.endswith('/'))
 prev=json.loads(z.read('manifest.sha256.json'))
 assert set(prev['files'])==old-{'manifest.sha256.json'}
 for name,digest in prev['files'].items():
  assert hashlib.sha256(z.read(name)).hexdigest()==digest,(name,'old manifest mismatch')
 print('Previous manifest validated:',len(prev['files']),'file hashes')
allfiles={str(p.relative_to(ROOT)):p for p in ROOT.rglob('*') if p.is_file() and '.git' not in p.relative_to(ROOT).parts}
new={}
for name,p in allfiles.items():
 segments=Path(name).parts
 if name in old or not any(x.startswith('__pycache__') or x=='.v620-classes' for x in segments):
  new[name]=p
 missing=old-set(new) if False else set()
assert old-set(new)==set(),f'Predecessor files absent: {sorted(old-set(new))[:12]}'
manifest={ 'algorithm':'sha256','checkpoint':'v0.62.0_P07_23_individually_verified_unmerged',
 'certification':'DEFERRED_NOT_CERTIFIED','clinical_release':'DENIED',
 'files':{k:hashlib.sha256(p.read_bytes()).hexdigest() for k,p in sorted(new.items()) if k!='manifest.sha256.json'}}
(ROOT/'manifest.sha256.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n')
with zipfile.ZipFile(DEST,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=7,allowZip64=True) as z:
 for name,p in sorted(new.items()):z.write(p,arcname=name)
with zipfile.ZipFile(DEST) as z:
 assert z.testzip() is None
 names=set(x for x in z.namelist() if not x.endswith('/'))
 actual=json.loads(z.read('manifest.sha256.json'))
 assert names==set(actual['files'])|{'manifest.sha256.json'}
 assert old<=names
 for name,digest in actual['files'].items():
  assert hashlib.sha256(z.read(name)).hexdigest()==digest,name
sha=hashlib.sha256(DEST.read_bytes()).hexdigest()
SUM.write_text(sha+'  '+DEST.name+'\n')
output={'archive':DEST.name,'sha256':sha,'archive_entries':len(names),
 'hashed_files':len(actual['files']),'prior_files_preserved':len(old),'new_file_paths':len(names-old),
 'verified_predecessor_entries':len(prev['files']), 'individual_batches_passed':23,'target_batches':100,
 'batches_not_completed':77,'real_postgresql_runtime_test':False,'certification':'DEFERRED_NOT_PASSED',
 'branch_lineage':'local v0.61.0 PR4, not merged with GitHub main newer-only history'}
PROOF.write_text(json.dumps(output,indent=2)+'\n')
print(json.dumps(output,indent=2))
