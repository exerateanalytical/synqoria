#!/usr/bin/env python3
"""Verify full cumulative ZIP paths, membership, hashes, and legacy/modern manifest formats."""
import pathlib, zipfile, json, hashlib, sys
MANIFEST='evidence/CUMULATIVE_FILE_SHA256.json'

def audit(path):
    issues=[]
    try:
        with zipfile.ZipFile(path) as z:
            names=z.namelist()
            if len(names)!=len(set(names)):issues.append('duplicate_zip_entries')
            for name in names:
                p=pathlib.PurePosixPath(name)
                if name.startswith('/') or '\\' in name or '..' in p.parts or not name or name.endswith('/'):
                    issues.append('unsafe_path:'+name)
                if z.getinfo(name).external_attr>>16 & 0o170000 == 0o120000:
                    issues.append('symlink:'+name)
            if MANIFEST not in names:return issues+['missing_manifest']
            data=json.loads(z.read(MANIFEST))
            files=data['files'] if isinstance(data,dict) and 'files' in data else data
            if not isinstance(files,dict):return issues+['invalid_manifest_structure']
            if set(names)!=set(files)|{MANIFEST}:issues.append('manifest_membership_mismatch')
            for name,digest in files.items():
                if not isinstance(name,str) or name==MANIFEST or name not in names or not isinstance(digest,str) \
                   or len(digest)!=64 or not all(c in '0123456789abcdef' for c in digest) \
                   or hashlib.sha256(z.read(name)).hexdigest()!=digest:
                    issues.append('digest_mismatch:'+str(name))
    except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile,RuntimeError) as ex:
        issues.append('invalid_archive:'+type(ex).__name__)
    return issues
if __name__=='__main__':
    issues=audit(sys.argv[1]);print(json.dumps({'verified':not issues,'issues':issues}));sys.exit(0 if not issues else 4)
