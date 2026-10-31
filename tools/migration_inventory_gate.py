"""Fail-closed Flyway migration inventory guard; source integrity, not database execution."""
from pathlib import Path
import re

NAME = re.compile(r'^V(\d+)__([a-z][a-z0-9_]*)\.sql$')

def audit(directory):
    directory = Path(directory)
    if not directory.is_dir():
        raise ValueError('migration directory missing')
    versions = {}
    for path in sorted(directory.iterdir()):
        if not path.is_file() or not path.name.lower().endswith('.sql'):
            continue
        match = NAME.fullmatch(path.name)
        if not match:
            raise ValueError(f'invalid migration filename: {path.name}')
        version = int(match.group(1))
        if version in versions:
            raise ValueError(f'duplicate Flyway version {version}: {versions[version]}, {path.name}')
        if path.stat().st_size == 0 or not path.read_text(encoding='utf-8').strip():
            raise ValueError(f'empty migration: {path.name}')
        versions[version] = path.name
    if not versions:
        raise ValueError('no migrations found')
    if min(versions) != 1 or set(versions) != set(range(1,max(versions)+1)):
        raise ValueError('migration version gap: '+str(sorted(versions)))
    return {'count':len(versions),'latest':max(versions),'versions':dict(sorted(versions.items()))}

if __name__ == '__main__':
    import json,sys
    print(json.dumps(audit(sys.argv[1]),indent=2))
