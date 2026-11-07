#!/usr/bin/env python3
"""Produce evidence-conservative SYNQORIA reconciliation after every merge.

Never infer 'built', 'ready', or 'accepted' from filenames. Code-signal
inventory and separately attested work evidence are intentionally distinct.
Runs with Python stdlib only; can be executed locally or in GitHub Actions.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULES = [f"M{i:02d}" for i in range(1, 41)]
SOURCE_SUFFIXES = {".java", ".ts", ".tsx", ".js", ".mjs", ".sql", ".go", ".py", ".cs"}
SKIP_PREFIXES = ("docs/", "registers/", "evidence/", "tests/", ".github/", "tools/", "archive/")
SOURCE_ROOTS = ("services/", "apps/", "packages/", "src/", "backend/", "frontend/")


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def paths_for_module(paths: list[str], module: str) -> list[str]:
    token = module.lower()
    pattern = re.compile(rf"(?<![a-z0-9]){token}(?![a-z0-9])", re.I)
    # Some merged module services have descriptive folder names instead of IDs.
    aliases = {
        "M38": ("services/offline-sync-core/", "apps/web/components/offline/", "apps/web/lib/offline/", "packages/offline-device-sdk/"),
        "M39": ("services/cell-lifecycle-core/", "apps/web/components/fleet/", "apps/web/lib/fleet/"),
        "M40": ("services/reliability-core/", "apps/web/components/reliability/", "apps/web/lib/reliability/"),
    }.get(module, ())
    found = []
    for path in paths:
        if path.startswith(SKIP_PREFIXES) or not path.startswith(SOURCE_ROOTS):
            continue
        if Path(path).suffix.lower() not in SOURCE_SUFFIXES:
            continue
        if pattern.search(path) or path.startswith(aliases):
            found.append(path)
    return sorted(set(found))


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def active_prs(data: object) -> list[dict]:
    if data is None:
        return []
    if isinstance(data, list) and data and all(isinstance(item, list) for item in data):
        data = [item for page in data for item in page]
    if not isinstance(data, list):
        raise ValueError("open_prs_not_a_list")
    result = []
    for item in data:
        if not isinstance(item, dict):
            raise ValueError("invalid_pr_record")
        if item.get("state") != "open":
            continue
        result.append({
            "number": item["number"],
            "title": item.get("title", ""),
            "head": (item.get("head") or {}).get("sha"),
            "branch": (item.get("head") or {}).get("ref"),
            "base": (item.get("base") or {}).get("ref"),
            "draft": item.get("draft", False),
        })
    return sorted(result, key=lambda item: item["number"])


def reconcile(paths: list[str], owners: dict, prs: list[dict], main_sha: str) -> dict:
    claims = owners.get("verifiedClaims", {})
    if sorted(claims) != sorted(key for key in claims if key in MODULES):
        raise ValueError("unknown_claim_module")
    rows = []
    for module in MODULES:
        code = paths_for_module(paths, module)
        claim = claims.get(module)
        status = "evidence_review_required"
        if claim is not None:
            if not isinstance(claim, dict) or not claim.get("pr") or not claim.get("scope"):
                raise ValueError("invalid_claim_evidence")
            # An attested engineering PR is not equivalent to module completion.
            status = "engineering_source_claimed_needs_system_acceptance"
        rows.append({
            "id": module,
            "codeSignals": len(code),
            "exampleSourcePaths": code[:12],
            "sourceEvidence": "located" if code else "not_located_by_path_heuristic",
            "implementation": status,
            "productionIntegrated": "not_verified",
            "clinicalAccepted": False,
            "attestedEngineeringEvidence": claim,
        })
    modules_with_code = sum(x["sourceEvidence"] == "located" for x in rows)
    return {
        "schemaVersion": "synqoria.reconciliation.v1",
        "generatedAtUtc": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "mainShaInspected": main_sha,
        "canonical": {"plannedModules": 40, "canonicalScreens": 261},
        "countingPolicy": {
            "locatedSource": "file naming signal only; NOT proof module is implemented",
            "missingSource": "no matching file path; NOT proof module is unbuilt",
            "engineeringClaim": "historical PR/handoff evidence, NOT production acceptance",
            "fullyBuiltModules": None,
            "unbuiltModules": None,
            "productionReadyPercent": None,
        },
        "summary": {
            "moduleEntries": len(rows),
            "modulesWithPathSignals": modules_with_code,
            "modulesWithoutPathSignals": 40 - modules_with_code,
            "confirmedUnbuiltModules": None,
            "confirmedFullyBuiltModules": None,
            "clinicallyAcceptedModules": 0,
            "openPrs": len(prs),
        },
        "activeWork": owners.get("activeWork", {}),
        "openPullRequests": prs,
        "modules": rows,
        "nextAction": "Resolve evidence gaps with an independently reviewed implementation manifest; never recreate a module on path-signal absence alone.",
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--open-prs", type=Path, help="JSON from GitHub API (required in CI)")
    parser.add_argument("--output", type=Path, default=ROOT / "registers/current-platform-reconciliation.json")
    args = parser.parse_args()
    paths = git("ls-files", "-z").split("\x00")
    owners = load_json(ROOT / "registers/engineering-ownership.json")
    prs = active_prs(load_json(args.open_prs)) if args.open_prs else []
    report = reconcile(paths, owners, prs, git("rev-parse", "HEAD"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("reconciled", report["mainShaInspected"][:12], "modules", report["summary"]["moduleEntries"],
          "path-signals", report["summary"]["modulesWithPathSignals"], "open-prs", len(prs))
    print("Unknown fully-built and unbuilt counts remain null until evidence-reviewed.")


if __name__ == "__main__":
    main()
