#!/usr/bin/env python3
"""Offline integrity and workspace consistency audit; does NOT generate a lockfile."""
import pathlib,json,re,sys
from urllib.parse import urlsplit
ROOT=pathlib.Path(__file__).resolve().parents[1]
WORKSPACES=('apps/web','apps/staff-mobile','apps/patient-mobile','packages/ux-core')
def audit(root):
 root=pathlib.Path(root);issues=[]
 try:
  lock=json.loads((root/'package-lock.json').read_text());main=json.loads((root/'package.json').read_text())
  packages=lock['packages']
 except (OSError,ValueError,KeyError,TypeError):return ['missing_or_invalid_lockfile']
 if lock.get('lockfileVersion')!=3:issues.append('lockfile_v3_required')
 if packages.get('',{}).get('version')!=main.get('version'):issues.append('root_version_mismatch')
 for w in WORKSPACES:
  try: manifest=json.loads((root/w/'package.json').read_text())
  except (OSError,ValueError):issues.append('invalid_manifest:'+w);continue
  locked=packages.get(w)
  if not isinstance(locked,dict):issues.append('workspace_missing:'+w);continue
  if locked.get('version')!=manifest.get('version'):issues.append('workspace_version:'+w)
  for field in ('dependencies','devDependencies'):
   for name,pin in manifest.get(field,{}).items():
    if locked.get(field,{}).get(name)!=pin:issues.append('workspace_dependency_mismatch:'+w+':'+name)
 for key,value in packages.items():
  if not key.startswith('node_modules/') or not isinstance(value,dict):continue
  if value.get('link'):
   if value.get('resolved') not in WORKSPACES:issues.append('unexpected_workspace_link:'+key)
   continue
  if 'version' not in value:issues.append('unversioned_package:'+key)
  if not re.fullmatch(r'sha512-[A-Za-z0-9+/]+={0,2}',str(value.get('integrity',''))):issues.append('missing_sha512_integrity:'+key)
  resolved=value.get('resolved','')
  parsed=urlsplit(resolved) if isinstance(resolved,str) else None
  if parsed is None or parsed.scheme!='https' or parsed.hostname!='registry.npmjs.org' or parsed.username or parsed.password or parsed.query or parsed.fragment or not parsed.path.startswith('/'):issues.append('untrusted_resolution:'+key)
 return issues
if __name__=='__main__':
 issues=audit(ROOT);print(json.dumps({'lock_integrity_verified':not issues,'issues':issues,'application_build_executed':False},indent=2));sys.exit(0 if not issues else 4)
