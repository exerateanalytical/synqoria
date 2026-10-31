#!/usr/bin/env python3
"""Manifest coherence and pinning audit. External Expo compatibility NOT certified."""
import json,re,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
WORKSPACES=('apps/web','apps/staff-mobile','apps/patient-mobile','packages/ux-core')
def audit(root):
 root=pathlib.Path(root);issues=[]
 try: main=json.loads((root/'package.json').read_text())
 except (OSError,ValueError):return ['invalid_root_manifest']
 version=main.get('version')
 if main.get('workspaces')!=list(WORKSPACES):issues.append('workspace_list_mismatch')
 versions={}
 for w in WORKSPACES:
  try: m=json.loads((root/w/'package.json').read_text())
  except (OSError,ValueError):issues.append('invalid_manifest:'+w);continue
  if m.get('version')!=version:issues.append('version_mismatch:'+w)
  for field in ('dependencies','devDependencies'):
   for name,pin in m.get(field,{}).items():
    if name.startswith('@synqoria/'):
     if pin!=version:issues.append('workspace_pin:'+w+':'+name)
    elif not isinstance(pin,str) or not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?',pin):issues.append('floating_pin:'+w+':'+name)
    versions[(w,name)]=pin
 for app in ('staff-mobile','patient-mobile'):
  for dep in ('expo','react','react-native','react-native-safe-area-context'):
   if ("apps/"+app,dep) not in versions:issues.append('missing_mobile_dependency:'+app+':'+dep)
 for dep in ('expo','react','react-native','react-native-safe-area-context'):
  if versions.get(('apps/staff-mobile',dep))!=versions.get(('apps/patient-mobile',dep)):issues.append('mobile_version_divergence:'+dep)
 return issues
if __name__=='__main__':
 issues=audit(ROOT);print(json.dumps({'source_pins_consistent':not issues,'issues':issues,'external_compatibility_verified':False,'lockfile_verified':False},indent=2));sys.exit(0 if not issues else 4)
