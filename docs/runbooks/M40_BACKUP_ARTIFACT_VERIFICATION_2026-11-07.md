# M40 independently inspected backup artifacts

`tools/m40_verify_backup_artifact.py` checks a **local, staged backup artifact** against the SHA-256 and byte count in the authenticated M40 backup catalogue. It streams bytes, does not follow final-component symlinks, rejects non-files, checks file metadata before and after reading, and returns a machine-readable JSON verdict without a filename or sensitive content.

Example using synthetic data:

```sh
python tools/m40_verify_backup_artifact.py \
  --artifact /isolated/readonly/synthetic-backup.dump \
  --expected-sha256 '<catalogue SHA-256 hex>' \
  --expected-size '<catalogue byte count>'
```

Exit status 0 means the observed local file matches both recorded attributes; status 2 means rejected. This does **not** mean the backup is restorable, complete, encrypted correctly, or authorized for clinical recovery.

## Operational integration requirements

1. Run this verifier under a **separate restricted identity** with read-only access to an immutable snapshot or content-addressed staged object, not under the backup producer's credentials.
2. Resolve expected SHA-256 and byte count from the authenticated M40 catalogue by a scoped backup ID, never from the backup producer's payload alone. The utility does not authenticate or query the catalogue itself.
3. Record a durable, unique evidence reference to the verification execution (job identifier and immutable logs) and the returned observed digest, then submit the `INTEGRITY` result to the M40 API with the independent `m40:backup_verify` role. Do not allow the recording actor to submit its own verification.
4. Independently exercise actual restore, WAL archive continuity, key usability, object references, and storage retention through the remaining four M40 verification checks.
5. A local file can be replaced before/after this program runs; stage read-only snapshots with controlled provenance. Checks on a mutable file are not cryptographic evidence of the original remote object.
6. The utility does not attest a physical backup, object storage inventory, authorization, restore success, WAL replay, RPO, RTO, HSM integration, or hospital deployment.

## Evidence

Run `python -m unittest discover -s tests -p 'test_m40_backup_artifact.py' -v`.
Tests use synthetic file content and validate good digests, corrupt digests, wrong sizes, symlink and directory denial, invalid arguments and privacy-safe failure output.
