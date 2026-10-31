#!/usr/bin/env python3
"""Fail-closed reproducible dependency gate. No lockfile = no app-build claim."""
import json,pathlib,re,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
WORKSPACES=('apps/web','apps/staff-mobile','apps/patient-mobile','packages/ux-core')
def audit(root):
 root=pathlib.Path(root);issues=[]
 try: main=json.loads((root/'package.json').read_text())
 except (OSError,ValueError):return ['missing_or_invalid_root_manifest']
 version=main.get('version');lock=root/'package-lock.json'
 if not lock.exists():issues.append('missing_package_lock')
 else:
  try:
   l=json.loads(lock.read_text());packages=l['packages']
   if l.get('lockfileVersion',0)<3:issues.append('unsupported_lock_version')
   if packages.get('',{}).get('version')!=version:issues.append('root_lock_version_mismatch')
   for w in WORKSPACES:
    if w not in packages:issues.append('missing_lock_workspace:'+w)
  except (ValueError,KeyError,TypeError):issues.append('invalid_package_lock')
 for w in WORKSPACES:
  try: m=json.loads((root/w/'package.json').read_text())
  except (OSError,ValueError):issues.append('missing_or_invalid_manifest:'+w);continue
  if m.get('version')!=version:issues.append('workspace_version_mismatch:'+w)
  for kind in ('dependencies','devDependencies'):
   for name,pin in m.get(kind,{}).items():
    if name.startswith('@synqoria/'):
     if pin!=version:issues.append('internal_workspace_pin_mismatch:'+w+':'+name)
    elif not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?',pin):
     issues.append('unfixed_external_dependency:'+w+':'+name)
 return issues
if __name__=='__main__':
 issues=audit(ROOT);print(json.dumps({'reproducible':not issues,'issues':issues,'clinical_release':False},indent=2));sys.exit(0 if not issues else 4)
