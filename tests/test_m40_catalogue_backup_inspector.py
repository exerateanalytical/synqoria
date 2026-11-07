"""Synthetic tests for independent catalogue-bound M40 artifact inspection."""
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from m40_catalogue_backup_inspector import (  # noqa: E402
    CatalogueUnavailable, inspect, safe_api_url, NoRedirect, MAX_RESPONSE_BYTES
)

class CatalogueInspectionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "synthetic.dump"
        self.data = b"synthetic-only-50-disposable-records" * 50
        self.path.write_bytes(self.data)
        self.backup_id = "00000000-0000-0000-0000-000000000123"
        self.record = {
            "id": self.backup_id,
            "digest": hashlib.sha256(self.data).hexdigest(),
            "sizeBytes": len(self.data),
            "status": "RECORDED",
        }

    def test_valid_record_still_not_claimed_verified_by_m40(self):
        result = inspect(self.path, self.backup_id, self.record)
        self.assertTrue(result["verified"])
        self.assertEqual(result["catalogueStatus"], "RECORDED")
        self.assertEqual(result["scope"], "INTEGRITY_ONLY_NOT_M40_VERIFICATION")

    def test_cross_record_substitution_refused(self):
        data = dict(self.record, id=str(uuid.uuid4()))
        with self.assertRaisesRegex(CatalogueUnavailable, "catalogue_identity_mismatch"):
            inspect(self.path, self.backup_id, data)

    def test_bad_metadata_rejected(self):
        for bad in [
            dict(self.record, digest="0" * 63),
            dict(self.record, sizeBytes=True),
            dict(self.record, sizeBytes="42"),
            dict(self.record, sizeBytes=-1),
        ]:
            with self.subTest(bad=bad):
                with self.assertRaisesRegex(CatalogueUnavailable, "catalogue_invalid_digest_or_size"):
                    inspect(self.path, self.backup_id, bad)

    def test_corrupt_artifact_is_denied(self):
        self.path.write_bytes(self.data[::-1])
        result = inspect(self.path, self.backup_id, self.record)
        self.assertFalse(result["verified"])
        self.assertEqual(result["reason"], "digest_mismatch")

    def test_will_not_follow_artifact_symlink(self):
        link = Path(self.tmp.name) / "linked"
        link.symlink_to(self.path)
        with self.assertRaisesRegex(RuntimeError, "artifact_unavailable"):
            inspect(link, self.backup_id, self.record)

    def test_https_endpoint_and_scope_are_mandatory(self):
        good = safe_api_url("https://m40.example.org/api/v1/reliability", self.backup_id)
        self.assertEqual(good, "https://m40.example.org/api/v1/reliability/backups/" + self.backup_id)
        for base in [
            "http://localhost/api/v1/reliability",
            "https://user:password@m40.example.org/api/v1/reliability",
            "https://m40.example.org/other/path",
            "https://m40.example.org/api/v1/reliability?redirect=1",
        ]:
            with self.subTest(base=base):
                with self.assertRaises(ValueError):
                    safe_api_url(base, self.backup_id)

    def test_redirects_denied(self):
        with self.assertRaisesRegex(CatalogueUnavailable, "catalogue_redirect_denied"):
            NoRedirect().redirect_request(None, None, 302, "redirect", {}, "https://other.example/")

    def test_cli_failure_never_leaks_path(self):
        run = subprocess.run(
            [sys.executable, str(ROOT / "tools" / "m40_catalogue_backup_inspector.py"),
             "--api-base", "http://invalid.example/api/v1/reliability",
             "--backup-id", self.backup_id, "--artifact", str(self.path),
             "--token-file", str(self.path)], capture_output=True, text=True, check=False)
        self.assertEqual(run.returncode, 2)
        result = json.loads(run.stdout)
        self.assertFalse(result["verified"])
        self.assertNotIn(str(self.path), run.stdout)
        self.assertEqual(result["reason"], "invalid_catalogue_endpoint")


if __name__ == "__main__":
    unittest.main()
