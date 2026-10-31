from pathlib import Path
import hashlib,json,sys,tempfile,zipfile
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'tools'))
from verify_cumulative_archive import audit
checks=0
# Previous version was a valid legacy plain mapping; verifier must not reject it.
legacy=Path('/mnt/data/synqoria_run/SYNQORIA_v0.40.0_cumulative.zip')
if legacy.exists():
 assert audit(legacy)==[],audit(legacy)
 checks+=1
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp)/'checkpoint.zip'
 data=b'synthetic evidence only';name='src/example.txt'
 manifest={'version':'test','files':{name:hashlib.sha256(data).hexdigest()}}
 with zipfile.ZipFile(p,'w') as z:
  z.writestr(name,data)
  z.writestr('evidence/CUMULATIVE_FILE_SHA256.json',json.dumps(manifest))
 assert audit(p)==[],audit(p);checks+=1
 with zipfile.ZipFile(p,'w') as z:
  z.writestr(name,b'tampered')
  z.writestr('evidence/CUMULATIVE_FILE_SHA256.json',json.dumps(manifest))
 assert any('digest_mismatch' in i for i in audit(p));checks+=1
 with zipfile.ZipFile(p,'w') as z:
  z.writestr('../escape.txt',b'x')
  z.writestr('evidence/CUMULATIVE_FILE_SHA256.json',json.dumps({'files':{'../escape.txt':hashlib.sha256(b'x').hexdigest()}}))
 assert any('unsafe_path' in i for i in audit(p));checks+=1
print(f'PASS cumulative archive verifier regression checks={checks}; no unverified archives accepted')
