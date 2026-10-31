"""P01-B0043: real-file integrity audit, synthetic fixture outputs only."""
import tempfile
from pathlib import Path
from tools.attest_app_builds import attest

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp)
    for directory, marker in [('apps/web/.next', 'BUILD_ID'),
                              ('apps/staff-mobile/dist', 'bundle.js'),
                              ('apps/patient-mobile/dist', 'bundle.js')]:
        target = root / directory
        target.mkdir(parents=True)
        (target / marker).write_text('synthetic fixture; not compiled application')
    checks = [{'name': name, 'exit_code': 0} for name in
              ('next-build', 'staff-expo-export', 'patient-expo-export')]
    valid = attest(root, checks)
    assert valid['status'] == 'ATTESTED' and len(valid['files']) == 3
    assert not valid['clinical_release'] and not valid['attestation_is_cryptographic_signature']
    assert all(len(x['sha256']) == 64 for x in valid['files'])
    assert attest(root, checks[:2])['status'] == 'BLOCKED'
    (root / 'apps/web/.next/BUILD_ID').unlink()
    assert 'missing_build_marker:next-build' in attest(root, checks)['issues']
    (root / 'apps/web/.next/BUILD_ID').write_text('fixture')
    (root / 'apps/patient-mobile/dist/bundle.js').unlink()
    (root / 'apps/patient-mobile/dist/bundle.js').symlink_to(root / 'apps/web/.next/BUILD_ID')
    assert 'symlink_in_build_output:patient-expo-export' in attest(root, checks)['issues']
    assert attest(root, [{'name': name, 'exit_code': 1} for name in
                         ('next-build', 'staff-expo-export', 'patient-expo-export')])['status'] == 'BLOCKED'
print('PASS P01-B0043: build artifact integrity, absent output, symlink, failure gates (synthetic fixtures)')
