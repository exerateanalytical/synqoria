#!/usr/bin/env python3
"""Offline Next/Expo dependency readiness; absence cannot be reported as a build pass."""
from pathlib import Path
import json,sys,subprocess
ROOT=Path(__file__).resolve().parents[1]
REQUIRED={'web':('next','react','react-dom'),'staff-mobile':('expo','react-native'),'patient-mobile':('expo','react-native')}
def audit(root):
    root=Path(root); issues=[]
    if not (root/'package-lock.json').is_file():issues.append('missing_root_lockfile')
    for app,modules in REQUIRED.items():
        f=root/'apps'/app/'package.json'
        if not f.is_file():issues.append('missing_manifest:'+app);continue
        manifest=json.loads(f.read_text());deps=manifest.get('dependencies',{})
        for module in modules:
            if module not in deps:issues.append('missing_declared_dependency:'+app+':'+module)
            if not (root/'node_modules'/module/'package.json').is_file() and not (root/'apps'/app/'node_modules'/module/'package.json').is_file():
                issues.append('missing_installed_dependency:'+app+':'+module)
        if manifest.get('version')!=json.loads((root/'package.json').read_text()).get('version'):
            issues.append('workspace_version_mismatch:'+app)
    return {'build_ready':not issues,'issues':issues,'scope':'dependency readiness only; no Next.js or native build executed'}
if __name__=='__main__':
    result=audit(Path(sys.argv[1]) if len(sys.argv)>1 else ROOT)
    print(json.dumps(result,indent=2));sys.exit(0 if result['build_ready'] else 4)
