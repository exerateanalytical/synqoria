#!/usr/bin/env python3
"""Attest real compiled app outputs only after successful build exit codes.

This is an integrity inventory, not a signature, independent review or clinical release.
"""
import hashlib
import json
import pathlib
import sys
import re

REQUIRED = {
    'next-build': ('apps/web/.next', 'BUILD_ID'),
    'staff-expo-export': ('apps/staff-mobile/dist', None),
    'patient-expo-export': ('apps/patient-mobile/dist', None),
}
MAX_FILES = 100000
MAX_FILE_BYTES = 512 * 1024 * 1024


def attest(root, checks):
    root = pathlib.Path(root).resolve()
    by_name = {c.get('name'): c for c in checks if isinstance(c, dict)}
    issues = []
    entries = []
    for name, (folder, marker) in REQUIRED.items():
        if name not in by_name or by_name[name].get('exit_code') != 0:
            issues.append('build_not_successful:' + name)
            continue
        output = root / folder
        if output.is_symlink() or not output.is_dir():
            issues.append('missing_or_symlinked_output:' + name)
            continue
        if marker and (not (output / marker).is_file() or (output / marker).is_symlink()):
            issues.append('missing_build_marker:' + name)
            continue
        count = 0
        for item in sorted(output.rglob('*')):
            if item.is_symlink():
                # Next 16 traces the installed PostgreSQL driver via a generated link.
                # Its exact target must equal the pinned root node_modules/pg;
                # no generic output symlinks, external targets, or stale packages.
                suffix = item.relative_to(output).as_posix()
                if name == 'next-build' and re.fullmatch(r'node_modules/pg-[0-9a-f]{16}', suffix):
                    try:
                        dependency = root / 'node_modules/pg'
                        resolved = item.resolve(strict=True)
                        if resolved != dependency.resolve(strict=True) or not resolved.is_dir():
                            raise ValueError('unexpected_dependency_target')
                        manifest = dependency / 'package.json'
                        info = json.loads(manifest.read_text())
                        lock = json.loads((root / 'package-lock.json').read_text())
                        version = lock['packages']['node_modules/pg']['version']
                        if info.get('name') != 'pg' or info.get('version') != version:
                            raise ValueError('unlocked_dependency_version')
                        if not re.fullmatch(r'[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?', version):
                            raise ValueError('invalid_dependency_version')
                        count += 1
                        entries.append({'path': item.relative_to(root).as_posix(),
                                        'kind': 'pinned_dependency_symlink',
                                        'target': 'node_modules/pg',
                                        'size': manifest.stat().st_size,
                                        'sha256': hashlib.sha256(manifest.read_bytes()).hexdigest()})
                        continue
                    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
                        print('BUILD_OUTPUT_SYMLINK_DENIED:', name, suffix,
                              type(exc).__name__, file=sys.stderr)
                        issues.append('symlink_dependency_lock_mismatch:' + name)
                        continue
                issues.append('symlink_in_build_output:' + name)
                print('BUILD_OUTPUT_SYMLINK_DENIED:', name, item.relative_to(root),
                      '->', item.readlink(), file=sys.stderr)
                continue
            if not item.is_file():
                continue
            count += 1
            if count > MAX_FILES:
                issues.append('excessive_build_files:' + name)
                break
            if item.stat().st_size > MAX_FILE_BYTES:
                issues.append('oversized_build_file:' + name)
                continue
            rel = item.relative_to(root).as_posix()
            entries.append({'path': rel, 'size': item.stat().st_size,
                            'sha256': hashlib.sha256(item.read_bytes()).hexdigest()})
        if count == 0:
            issues.append('empty_build_output:' + name)
    return {'status': 'ATTESTED' if not issues else 'BLOCKED', 'issues': issues,
            'files': entries if not issues else [], 'clinical_release': False,
            'attestation_is_cryptographic_signature': False}


if __name__ == '__main__':
    root = pathlib.Path(__file__).resolve().parents[1]
    evidence = root / 'evidence/batches/v280-actual-build-attempt.json'
    try:
        attempt = json.loads(evidence.read_text())
    except (OSError, ValueError):
        print(json.dumps({'status': 'BLOCKED', 'issues': ['no_actual_build_evidence']}))
        sys.exit(4)
    result = attest(root, attempt.get('checks', []))
    print(json.dumps(result, indent=2))
    sys.exit(0 if result['status'] == 'ATTESTED' else 4)
