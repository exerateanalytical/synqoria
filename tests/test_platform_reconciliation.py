"""Deterministic safeguards: file signals never imply a completed module."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from reconcile_platform import MODULES, active_prs, paths_for_module, reconcile


class ReconciliationTests(unittest.TestCase):
    def test_exactly_forty_canonical_modules(self):
        self.assertEqual(len(MODULES), 40)
        self.assertEqual(MODULES[0], "M01")
        self.assertEqual(MODULES[-1], "M40")

    def test_excludes_docs_and_unrelated_module_evidence(self):
        paths = [
            "docs/engineering/M38_THREAT_MODEL.md",
            "registers/M38-design.json",
            "services/offline-sync-core/src/main/java/org/synqoria/offline/Api.java",
            "services/reliability-core/src/main/java/org/synqoria/reliability/Api.java",
            "apps/web/components/offline/DevicePanel.tsx",
            "tools/m40_verify_backup_artifact.py",
        ]
        self.assertEqual(len(paths_for_module(paths, "M38")), 2)
        self.assertEqual(len(paths_for_module(paths, "M40")), 1)
        self.assertEqual(len(paths_for_module(paths, "M01")), 0)

    def test_never_infers_unbuilt_or_fully_built_from_file_counts(self):
        report = reconcile(["services/cell-lifecycle-core/Api.java"],
                           {"activeWork": {}, "verifiedClaims": {}}, [], "f" * 40)
        self.assertEqual(len(report["modules"]), 40)
        self.assertIsNone(report["summary"]["confirmedUnbuiltModules"])
        self.assertIsNone(report["summary"]["confirmedFullyBuiltModules"])
        self.assertIsNone(report["countingPolicy"]["productionReadyPercent"])
        self.assertEqual(report["summary"]["modulesWithPathSignals"], 1)
        self.assertEqual(report["summary"]["modulesWithoutPathSignals"], 39)
        self.assertEqual(report["summary"]["clinicallyAcceptedModules"], 0)

    def test_historical_engineering_claim_is_not_acceptance(self):
        report = reconcile([], {"activeWork": {}, "verifiedClaims": {
            "M39": {"pr": 51, "scope": "source only"}
        }}, [], "a" * 40)
        row = next(m for m in report["modules"] if m["id"] == "M39")
        self.assertEqual(row["implementation"], "engineering_source_claimed_needs_system_acceptance")
        self.assertFalse(row["clinicalAccepted"])
        self.assertEqual(row["productionIntegrated"], "not_verified")

    def test_active_pull_requests_flat_and_paginated(self):
        items = [[{"state": "closed", "number": 50}, {
            "state": "open", "number": 56, "title": "M38", "head": {"sha": "abc", "ref": "work/m38"},
            "base": {"ref": "main"}, "draft": False}]]
        result = active_prs(items)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["number"], 56)

    def test_invalid_evidence_refused(self):
        with self.assertRaisesRegex(ValueError, "unknown_claim_module"):
            reconcile([], {"verifiedClaims": {"M41": {"pr": 1, "scope": "x"}}}, [], "1"*40)
        with self.assertRaisesRegex(ValueError, "invalid_claim_evidence"):
            reconcile([], {"verifiedClaims": {"M02": {"pr": 2}}}, [], "1"*40)


if __name__ == "__main__":
    unittest.main()
