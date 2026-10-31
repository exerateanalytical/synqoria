"""Immutable migration checksum gate. Baseline changes require reviewed, explicit lock update."""
from pathlib import Path
import hashlib,json,re
from migration_inventory_gate import audit

def verify(migrations,lock):
    migrations=Path(migrations); lock=Path(lock)
    inventory=audit(migrations)
    if not lock.is_file():
        raise ValueError('migration checksum lock missing')
    data=json.loads(lock.read_text(encoding='utf-8'))
    if data.get('algorithm')!='sha256' or not isinstance(data.get('files'),dict):
        raise ValueError('invalid checksum lock format')
    expected=data['files']
    for name,digest in expected.items():
        if not re.fullmatch(r'[a-f0-9]{64}',digest):
            raise ValueError('invalid checksum digest for '+name)
        path=migrations/name
        if not path.is_file():
            raise ValueError('locked migration missing: '+name)
        if hashlib.sha256(path.read_bytes()).hexdigest()!=digest:
            raise ValueError('immutable migration modified: '+name)
    actual=set(inventory['versions'].values())
    if actual!=set(expected):
        raise ValueError('migration lock inventory mismatch: missing='+str(sorted(actual-set(expected)))+' extra='+str(sorted(set(expected)-actual)))
    return {'locked':len(expected),'latest':inventory['latest']}

if __name__=='__main__':
    import sys
    print(json.dumps(verify(sys.argv[1],sys.argv[2]),sort_keys=True))
