#!/usr/bin/env python3
"""Prepare precise UTF-8 source delta relative to verified parent archive for GitHub API transfer."""
import zipfile,hashlib,json,base64
from pathlib import Path
R=Path(__file__).resolve().parents[1];PARENT=Path('/mnt/data/SYNQORIA_v0.62.0_P07_23BATCH_cumulative.zip')
parts=[];names=[]
with zipfile.ZipFile(PARENT) as z:
 old=json.loads(z.read('manifest.sha256.json'))['files']
 for path in sorted(R.rglob('*')):
  if not path.is_file():continue
  name=str(path.relative_to(R))
  if any(p in {'.v630-classes','.v620-classes','__pycache__','.git','dist','node_modules'} for p in path.relative_to(R).parts):continue
  if name=='manifest.sha256.json':continue
  raw=path.read_bytes();digest=hashlib.sha256(raw).hexdigest()
  if old.get(name)==digest:continue
  try:body=raw.decode('utf-8')
  except UnicodeDecodeError:
   raise ValueError('changed binary source requires blob upload: '+name)
  names.append((name,len(raw),name not in old))
  pieces=[body[i:i+18000] for i in range(0,len(body),18000)] or ['']
  for index,chunk in enumerate(pieces):parts.append({'path':name,'part':index,'parts':len(pieces),'sha256':digest,'content':chunk})
print('Tracked changed/new files:',len(names),'total bytes:',sum(n[1] for n in names),'new paths:',sum(n[2] for n in names))
for n in names:print(('NEW' if n[2] else 'MOD'),n[0],n[1])
chunks=[];current=[];size=0
for entry in parts:
 raw=len(json.dumps(entry,ensure_ascii=False).encode())
 if current and size+raw>96000:
  chunks.append(current);current=[];size=0
 current.append(entry);size+=raw
if current:chunks.append(current)
for i,c in enumerate(chunks):
 file=Path(f'/mnt/data/SYNQORIA_V630_GITHUB_BRIDGE_{i:02d}.json')
 file.write_text(json.dumps({'version':'0.63.0','total':len(chunks),'entries':c},ensure_ascii=False,separators=(',',':')))
 print('BRIDGE',i,len(c),'size',file.stat().st_size)
Path('/mnt/data/SYNQORIA_v0.63.0_GITHUB_PATCH_INDEX.json').write_text(json.dumps({'count':len(names),'files':names,'chunks':len(chunks)},indent=2))
