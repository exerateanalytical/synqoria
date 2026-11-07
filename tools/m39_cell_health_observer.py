#!/usr/bin/env python3
"""M39 fail-closed, externally observed cell readiness reporter.

This is a single-shot command, suitable for a workload-identity scheduled job.
It never provisions a cell. A missing/failed probe is UNHEALTHY, never HEALTHY.
The service remains authoritative for recorded observations and fleet routing.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import stat
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone

MAX_BYTES = 4096


class ProbeError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise ProbeError("redirect_denied")


def require_https(url: str) -> str:
    try:
        p = urllib.parse.urlsplit(url)
        port = p.port
    except ValueError as exc:
        raise ProbeError("invalid_endpoint") from exc
    if (p.scheme != "https" or not p.hostname or p.username or p.password
            or p.fragment or p.query or port == 0 or not p.path.startswith("/")):
        raise ProbeError("invalid_endpoint")
    return url


def check_ready(url: str, opener=None) -> bool:
    require_https(url)
    opener = opener or urllib.request.build_opener(NoRedirect())
    req = urllib.request.Request(url, headers={"Accept": "application/json",
                                               "Cache-Control": "no-store"}, method="GET")
    try:
        with opener.open(req, timeout=5) as response:
            if response.status != 200 or "application/json" not in response.headers.get("Content-Type", "").lower():
                return False
            raw = response.read(MAX_BYTES + 1)
            if len(raw) > MAX_BYTES:
                return False
            data = json.loads(raw)
            # Explicit positive evidence, not HTTP 200 alone.
            return isinstance(data, dict) and data.get("ready") is True
    except (urllib.error.URLError, TimeoutError, OSError, ValueError, ProbeError):
        return False


def report_status(probe_urls: list[str], opener=None) -> str:
    if len(probe_urls) < 2 or len(set(probe_urls)) != len(probe_urls):
        raise ProbeError("minimum_two_independent_probes_required")
    for url in probe_urls:
        require_https(url)
    results = [check_ready(url, opener) for url in probe_urls]
    return "HEALTHY" if all(results) else "UNHEALTHY"


def send_health(api_base: str, cell_id: str, token: str, status: str,
                timestamp: datetime, opener=None) -> None:
    require_https(api_base)
    if urllib.parse.urlsplit(api_base).path.rstrip("/") != "/api/v1/fleet":
        raise ProbeError("invalid_endpoint")
    try:
        uid = str(uuid.UUID(cell_id))
    except ValueError as exc:
        raise ProbeError("invalid_cell_id") from exc
    if not token or "\r" in token or "\n" in token or len(token) > 8192:
        raise ProbeError("invalid_token")
    if status not in ("HEALTHY", "UNHEALTHY"):
        raise ProbeError("invalid_status")
    url = api_base.rstrip("/") + "/cells/" + uid + "/health"
    payload = json.dumps({"status": status,
                          "observedAt": timestamp.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
                          "releaseKey": None}, separators=(",", ":")).encode()
    req = urllib.request.Request(url, data=payload, headers={
        "Authorization": "Bearer " + token, "Idempotency-Key": str(uuid.uuid4()),
        "Content-Type": "application/json", "Accept": "application/json",
        "Cache-Control": "no-store"}, method="POST")
    opener = opener or urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(req, timeout=10) as response:
            if response.status != 201:
                raise ProbeError("health_report_not_accepted")
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise ProbeError("health_report_unavailable") from exc


def read_token_file(path: Path) -> str:
    # Never accept a symlink, shared readable secret, or non-regular file.
    if path.is_symlink():
        raise ProbeError("token_file_denied")
    s = path.stat()
    if not stat.S_ISREG(s.st_mode) or s.st_mode & 0o077:
        raise ProbeError("token_file_denied")
    return path.read_text(encoding="utf-8").strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="M39 two-probe cell health observer")
    parser.add_argument("--probe", action="append", required=True, help="trusted HTTPS readiness endpoint, twice or more")
    parser.add_argument("--api-base", required=True, help="https://management.example/api/v1/fleet")
    parser.add_argument("--cell-id", required=True, help="target hospital cell UUID")
    parser.add_argument("--token-file", type=Path, required=True)
    args = parser.parse_args()
    try:
        # Fail before network calls if configuration is malformed.
        require_https(args.api_base)
        status = report_status(args.probe)
        token = read_token_file(args.token_file)
        send_health(args.api_base, args.cell_id, token, status, datetime.now(timezone.utc))
        result = {"submitted": True, "status": status,
                  "scope": "PROBE_OBSERVATION_NOT_HOSPITAL_QUALIFICATION"}
        code = 0 if status == "HEALTHY" else 2
    except (ProbeError, OSError, ValueError, UnicodeError) as exc:
        allowed = {"minimum_two_independent_probes_required", "invalid_endpoint",
                   "invalid_cell_id", "invalid_token", "invalid_status", "redirect_denied",
                   "token_file_denied", "health_report_unavailable",
                   "health_report_not_accepted"}
        result = {"submitted": False,
                  "reason": str(exc) if str(exc) in allowed else "probe_execution_failed",
                  "scope": "PROBE_OBSERVATION_NOT_HOSPITAL_QUALIFICATION"}
        code = 2
    print(json.dumps(result, sort_keys=True))
    return code


if __name__ == "__main__":
    sys.exit(main())
