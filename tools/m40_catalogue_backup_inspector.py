#!/usr/bin/env python3
"""Read-only M40 catalogue-backed artifact inspection.

Retrieves a scoped backup catalogue row from the M40 API, compares an
independently staged local artifact to its recorded digest and size, and emits
a limited verdict. It does NOT submit the five-check verification decision,
which requires an independent authorised reviewer and restore evidence.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any, Callable

from m40_verify_backup_artifact import verify

MAX_RESPONSE_BYTES = 131072
HEX = re.compile(r"[0-9a-f]{64}\Z")


class CatalogueUnavailable(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise CatalogueUnavailable("catalogue_redirect_denied")


def safe_api_url(base: str, backup_id: str) -> str:
    try:
        uid = str(uuid.UUID(backup_id))
    except (TypeError, ValueError) as exc:
        raise ValueError("invalid_backup_id") from exc
    url = urllib.parse.urlsplit(base)
    if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment or url.port == 0:
        raise ValueError("invalid_catalogue_endpoint")
    if url.path.rstrip("/") != "/api/v1/reliability":
        raise ValueError("invalid_catalogue_endpoint")
    return urllib.parse.urlunsplit((url.scheme, url.netloc, url.path.rstrip("/") + "/backups/" + uid, "", ""))


def get_backup(url: str, token: str) -> dict[str, Any]:
    if not token or "\n" in token or "\r" in token or len(token) > 8192:
        raise ValueError("invalid_access_token")
    opener = urllib.request.build_opener(
        NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context()))
    req = urllib.request.Request(url, headers={
        "Authorization": "Bearer " + token, "Accept": "application/json",
        "Cache-Control": "no-store", "User-Agent": "synqoria-m40-integrity/1",
    }, method="GET")
    try:
        with opener.open(req, timeout=10) as response:
            if response.status != 200:
                raise CatalogueUnavailable("catalogue_http_error")
            if "application/json" not in response.headers.get("Content-Type", "").lower():
                raise CatalogueUnavailable("catalogue_content_type_denied")
            content = response.read(MAX_RESPONSE_BYTES + 1)
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise CatalogueUnavailable("catalogue_unavailable") from exc
    if len(content) > MAX_RESPONSE_BYTES:
        raise CatalogueUnavailable("catalogue_response_too_large")
    try:
        record = json.loads(content)
    except (ValueError, UnicodeError) as exc:
        raise CatalogueUnavailable("catalogue_invalid_json") from exc
    if not isinstance(record, dict):
        raise CatalogueUnavailable("catalogue_invalid_record")
    return record


def inspect(artifact: Path, expected_id: str, record: dict[str, Any]) -> dict[str, Any]:
    try:
        uid = str(uuid.UUID(expected_id))
    except (ValueError, TypeError) as exc:
        raise ValueError("invalid_backup_id") from exc
    if record.get("id") != uid:
        raise CatalogueUnavailable("catalogue_identity_mismatch")
    sha = record.get("digest")
    size = record.get("sizeBytes")
    if not isinstance(sha, str) or HEX.fullmatch(sha) is None or type(size) is not int or size <= 0:
        raise CatalogueUnavailable("catalogue_invalid_digest_or_size")
    # A failed or unverified catalogue entry can still be independently inspected;
    # integrity alone never upgrades its M40 status.
    checked = verify(artifact, sha, size)
    return {"verified": bool(checked["verified"]), "reason": checked["reason"],
            "sizeBytes": checked.get("sizeBytes"), "observedDigest": checked.get("observedDigest"),
            "catalogueStatus": record.get("status") if record.get("status") in
             ("RECORDED", "VERIFIED", "VERIFICATION_FAILED") else "UNKNOWN",
            "scope": "INTEGRITY_ONLY_NOT_M40_VERIFICATION"}


def main() -> int:
    p = argparse.ArgumentParser(description="Inspect artifact against authenticated M40 catalogue")
    p.add_argument("--api-base", required=True, help="https://HOST/api/v1/reliability")
    p.add_argument("--backup-id", required=True)
    p.add_argument("--artifact", type=Path, required=True, help="independently staged read-only artifact")
    p.add_argument("--token-file", type=Path, required=True, help="isolated OIDC bearer token file")
    args = p.parse_args()
    try:
        url = safe_api_url(args.api_base, args.backup_id)
        mode = args.token_file.stat().st_mode
        if mode & 0o077:
            raise ValueError("token_file_permissions_too_broad")
        token = args.token_file.read_text(encoding="utf-8").strip()
        record = get_backup(url, token)
        result = inspect(args.artifact, args.backup_id, record)
    except (ValueError, OSError, CatalogueUnavailable, RuntimeError) as exc:
        # Never echo the exception's underlying network, file or secret details.
        reason = str(exc)
        allowed = {
            "invalid_backup_id", "invalid_catalogue_endpoint", "invalid_access_token",
            "token_file_permissions_too_broad", "catalogue_redirect_denied",
            "catalogue_http_error", "catalogue_content_type_denied", "catalogue_unavailable",
            "catalogue_response_too_large", "catalogue_invalid_json",
            "catalogue_invalid_record", "catalogue_identity_mismatch",
            "catalogue_invalid_digest_or_size", "artifact_unavailable",
            "artifact_not_regular", "nofollow_unavailable",
        }
        result = {"verified": False, "reason": reason if reason in allowed else "inspection_failed",
                  "scope": "INTEGRITY_ONLY_NOT_M40_VERIFICATION"}
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0 if result["verified"] else 2


if __name__ == "__main__":
    sys.exit(main())
