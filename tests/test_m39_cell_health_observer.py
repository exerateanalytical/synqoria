"""Synthetic-only regression checks for M39 cell health observer."""
import json
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from m39_cell_health_observer import (
    ProbeError, check_ready, report_status, send_health, read_token_file, require_https
)


class Response:
    def __init__(self, status=200, payload=b'{"ready":true}'):
        self.status = status
        self.headers = {"Content-Type": "application/json"}
        self.payload = payload
    def __enter__(self):
        return self
    def __exit__(self, *_):
        return False
    def read(self, count):
        return self.payload[:count]


class Opener:
    def __init__(self, responses):
        self.responses = responses
        self.requests = []
    def open(self, request, timeout=None):
        self.requests.append(request)
        return self.responses.pop(0)


class CellObserverTests(unittest.TestCase):
    def setUp(self):
        self.urls = ["https://a.cell.example/ready", "https://b.cell.example/ready"]
        self.cell_id = "00000000-0000-0000-0000-000000000111"

    def test_two_real_positive_responses_required(self):
        op = Opener([Response(), Response()])
        self.assertEqual(report_status(self.urls, op), "HEALTHY")
        self.assertEqual(len(op.requests), 2)

    def test_single_failed_probe_fails_closed(self):
        for bad in [Response(503), Response(payload=b'{"ready":false}'),
                    Response(payload=b'{"status":"ok"}'), Response(payload=b'[]')]:
            with self.subTest(bad=bad):
                self.assertEqual(report_status(self.urls, Opener([Response(), bad])), "UNHEALTHY")

    def test_rejects_single_or_duplicate_probes(self):
        for urls in [[self.urls[0]], [self.urls[0], self.urls[0]]]:
            with self.assertRaisesRegex(ProbeError, "minimum_two"):
                report_status(urls)

    def test_plain_http_or_redirect_target_not_accepted(self):
        for url in ["http://example.org/ready", "https://user:pass@example.org/ready",
                    "https://example.org/ready?bypass=true"]:
            with self.subTest(url=url):
                with self.assertRaisesRegex(ProbeError, "invalid_endpoint"):
                    require_https(url)

    def test_report_posts_actual_observation_and_idempotency(self):
        op = Opener([Response(status=201)])
        stamp = datetime(2026, 11, 7, 10, 0, tzinfo=timezone.utc)
        send_health("https://fleet.internal.example/api/v1/fleet",
                    self.cell_id, "fake-synthetic-token", "UNHEALTHY", stamp, op)
        sent = op.requests[0]
        self.assertEqual(sent.get_method(), "POST")
        self.assertEqual(sent.headers["Idempotency-key"] is not None, True)
        body = json.loads(sent.data)
        self.assertEqual(body["status"], "UNHEALTHY")
        self.assertEqual(body["observedAt"], "2026-11-07T10:00:00Z")
        self.assertEqual(body["releaseKey"], None)

    def test_non_acceptance_does_not_succeed(self):
        with self.assertRaisesRegex(ProbeError, "health_report_not_accepted"):
            send_health("https://fleet.internal.example/api/v1/fleet",
                        self.cell_id, "fake-synthetic-token", "HEALTHY",
                        datetime.now(timezone.utc), Opener([Response(status=403)]))

    def test_rejects_nonprivate_token_file_and_symlinks(self):
        with tempfile.TemporaryDirectory() as directory:
            token = Path(directory) / "token"
            token.write_text("synthetic")
            token.chmod(0o644)
            with self.assertRaisesRegex(ProbeError, "token_file_denied"):
                read_token_file(token)
            token.chmod(0o600)
            self.assertEqual(read_token_file(token), "synthetic")
            alias = Path(directory) / "alias"
            alias.symlink_to(token)
            with self.assertRaisesRegex(ProbeError, "token_file_denied"):
                read_token_file(alias)


if __name__ == "__main__":
    unittest.main()
