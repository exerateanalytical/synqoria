#!/usr/bin/env python3
"""M40 independent artifact integrity check for a locally staged backup.

This tool verifies *bytes and size*, not that a backup can be restored. It must
run under an isolated verifier identity against a trusted, read-only artifact
copy; expected digest/size must come from the authenticated backup catalogue.
No patient data, file path or secret is written to stdout.
"""
from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import re
import stat
import sys
from pathlib import Path

CHUNK_BYTES = 1024 * 1024
SHA256_HEX = re.compile(r"[0-9a-f]{64}\Z")


def verify(path: Path, expected_digest: str, expected_size: int) -> dict[str, object]:
    if not SHA256_HEX.fullmatch(expected_digest):
        raise ValueError("invalid_expected_digest")
    if expected_size <= 0:
        raise ValueError("invalid_expected_size")

    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0)
    if not hasattr(os, "O_NOFOLLOW"):
        raise RuntimeError("nofollow_unavailable")
    flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise RuntimeError("artifact_unavailable") from exc

    try:
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode):
            raise RuntimeError("artifact_not_regular")
        if before.st_size != expected_size:
            return {"verified": False, "reason": "size_mismatch", "sizeBytes": before.st_size}
        digest = hashlib.sha256()
        total = 0
        with os.fdopen(os.dup(descriptor), "rb", closefd=True) as handle:
            while True:
                chunk = handle.read(CHUNK_BYTES)
                if not chunk:
                    break
                total += len(chunk)
                if total > expected_size:
                    return {"verified": False, "reason": "size_changed", "sizeBytes": total}
                digest.update(chunk)

        after = os.fstat(descriptor)
        if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns,
            before.st_ctime_ns) != (after.st_dev, after.st_ino, after.st_size,
                                   after.st_mtime_ns, after.st_ctime_ns):
            return {"verified": False, "reason": "artifact_modified_during_read", "sizeBytes": total}
        if total != expected_size:
            return {"verified": False, "reason": "size_changed", "sizeBytes": total}
        actual = digest.hexdigest()
        if not hmac.compare_digest(actual, expected_digest):
            return {"verified": False, "reason": "digest_mismatch", "sizeBytes": total}
        return {"verified": True, "reason": "digest_and_size_match", "sizeBytes": total,
                "observedDigest": actual}
    finally:
        os.close(descriptor)


def main() -> int:
    parser = argparse.ArgumentParser(description="M40 read-only backup digest verifier")
    parser.add_argument("--artifact", required=True, type=Path, help="trusted local staged file")
    parser.add_argument("--expected-sha256", required=True, help="digest from authenticated backup catalogue")
    parser.add_argument("--expected-size", required=True, type=int, help="size from authenticated backup catalogue")
    args = parser.parse_args()
    try:
        result = verify(args.artifact, args.expected_sha256, args.expected_size)
    except (OSError, RuntimeError, ValueError) as exc:
        result = {"verified": False, "reason": str(exc)}
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0 if result["verified"] else 2


if __name__ == "__main__":
    sys.exit(main())
