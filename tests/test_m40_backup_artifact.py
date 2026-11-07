"""Synthetic-only regression tests for the M40 standalone artifact checker."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from m40_verify_backup_artifact import verify  # noqa: E402


class ArtifactVerificationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "synthetic.backup"
        self.payload = b"synthetic-disposable-hospital-cell-fixture" * 100
        self.path.write_bytes(self.payload)
        self.digest = hashlib.sha256(self.payload).hexdigest()

    def test_good_artifact(self):
        result = verify(self.path, self.digest, len(self.payload))
        self.assertTrue(result["verified"])
        self.assertEqual(result["observedDigest"], self.digest)

    def test_corrupted_content_rejected(self):
        self.path.write_bytes(self.payload[::-1])
        result = verify(self.path, self.digest, len(self.payload))
        self.assertFalse(result["verified"])
        self.assertEqual(result["reason"], "digest_mismatch")

    def test_wrong_size_rejected(self):
        result = verify(self.path, self.digest, len(self.payload) + 1)
        self.assertFalse(result["verified"])
        self.assertEqual(result["reason"], "size_mismatch")

    def test_symlink_rejected(self):
        link = Path(self.tmp.name) / "linked.backup"
        link.symlink_to(self.path)
        with self.assertRaises(RuntimeError):
            verify(link, self.digest, len(self.payload))

    def test_directory_rejected(self):
        with self.assertRaises(RuntimeError):
            verify(Path(self.tmp.name), self.digest, len(self.payload))

    def test_malformed_inputs_rejected(self):
        with self.assertRaises(ValueError):
            verify(self.path, "wrong", len(self.payload))
        with self.assertRaises(ValueError):
            verify(self.path, self.digest, 0)

    def test_cli_error_has_no_artifact_path(self):
        run = subprocess.run(
            [sys.executable, str(ROOT / "tools" / "m40_verify_backup_artifact.py"),
             "--artifact", str(self.path), "--expected-sha256", self.digest,
             "--expected-size", str(len(self.payload) + 1)],
            check=False, capture_output=True, text=True)
        self.assertEqual(run.returncode, 2)
        data = json.loads(run.stdout)
        self.assertFalse(data["verified"])
        self.assertNotIn(str(self.path), run.stdout)


if __name__ == "__main__":
    unittest.main()
