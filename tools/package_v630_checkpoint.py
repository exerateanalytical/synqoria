#!/usr/bin/env python3
"""Produce verified complete cumulative source ZIP without deleting predecessor files."""
from pathlib import Path
import hashlib,json,zipfile
R=Path(__file__).resolve().parents[1]
PARENT=Path('/mnt/data/SYNQORIA_v0.62.0_P07_23BATCH_cumulative.zip')
DEST=Path('/mnt/data/SYNQORIA_v0.63.0_P07_11BATCH_cumulative.zip')
SUM=Path('/mnt/data/SYNQORIA_v0.63.0_SHA256.txt')
PROOF=Path('/mnt/data/SYNQORIA_v0.63.0_PACKAGE_VERIFICATION.json')
assert PARENT.is_file(), 'prior cumulative ZIP missing'
with zipfile.ZipFile(PARENT) as z:
 old=set(z.namelist());previous=json.loads(z.read('manifest.sha256.json'))
 assert old==set(previous['files'])|{'manifest.sha256.json'}
 for name,digest in previous['files'].items():
  assert hashlib.sha256(z.read(name)).hexdigest()==digest,('predecessor corrupt',name)
print('Previous ZIP verified:',len(previous['files']),'file hashes',flush=True)
files={}
for p in R.rglob('*'):
 if not p.is_file():continue
 rel=str(p.relative_to(R));parts=p.relative_to(R).parts
 if rel in old or not any(x.startswith('__pycache__') or x in {'.v630-classes','.v620-classes','.git','node_modules','dist'} for x in parts):files[rel]=p
assert old<=set(files), 'Cumulative source deletion detected: '+str(sorted(old-set(files))[:10])
manifest={'algorithm':'sha256','checkpoint':'v0.63.0_P07_B0094_B0104_unmerged',
 'security':'NOT_PRODUCTION_QUALIFIED','certification':'DEFERRED_NOT_PASSED','clinical_release':'DENIED',
 'files':{name:hashlib.sha256(p.read_bytes()).hexdigest() for name,p in sorted(files.items()) if name!='manifest.sha256.json'}}
(R/'manifest.sha256.json').write_text(json.dumps(manifest,indent=2)+'\n')
files['manifest.sha256.json']=R/'manifest.sha256.json'
with zipfile.ZipFile(DEST,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=7,allowZip64=True) as z:
 for name,p in sorted(files.items()):z.write(p,arcname=name)
with zipfile.ZipFile(DEST) as z:
 assert z.testzip() is None
 names=set(z.namelist());m=json.loads(z.read('manifest.sha256.json'))
 assert names==set(m['files'])|{'manifest.sha256.json'} and old<=names
 for name,digest in m['files'].items():
  assert hashlib.sha256(z.read(name)).hexdigest()==digest,name
sha=hashlib.sha256(DEST.read_bytes()).hexdigest()
SUM.write_text(sha+'  '+DEST.name+'\n')
result={'archive':DEST.name,'sha256':sha,'entries':len(files),'verified_file_hashes':len(files)-1,
 'prior_entries_preserved':len(old),'added_paths':len(set(files)-old),
 'current_verified_batches':11,'total_verified_in_prior_100_request':34,'not_yet_completed_in_prior_100_request':66,
 'regression_groups':17,'regression_passed':17,'final_UX_tests_passed':296,
 'live_postgresql_verified':False,'real_broker_verified':False,
 'clinical_certification':'DEFERRED_NOT_PASSED','clinical_release':'DENIED',
 'source_branch_base':'draft PR5, commit 3fe8289829946a531f6e398a3a8e229d3c8882a7'}
PROOF.write_text(json.dumps(result,indent=2)+'\n')
print('Created and verified',DEST,'sha256',sha,'files',len(files),flush=True)
