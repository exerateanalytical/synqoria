"""Positive and negative tests for the M01-M37 forensic reconciliation evidence (Issue #60, package 29).

Run: python -m unittest discover -s tests/reconciliation -v
Set RECON_RESCAN=1 to also require byte-identical regeneration at the recorded baseline (needs full git history; ~2 minutes).
"""
import copy, hashlib, json, os, shutil, subprocess, sys, tempfile, unittest

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(REPO, "registers", "reconciliation")
sys.path.insert(0, os.path.join(REPO, "tools", "reconciliation"))
import validate_reconciliation as V  # noqa: E402


def baseline_available():
    sha = json.load(open(os.path.join(OUT, "manifest.json"), encoding="utf-8"))["baselineSha"]
    return subprocess.run(["git", "-C", REPO, "cat-file", "-e", sha + "^{commit}"], capture_output=True).returncode == 0


HAVE_BASELINE = baseline_available()


class Mutable(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="recon-")
        self.d = os.path.join(self.tmp, "r")
        shutil.copytree(OUT, self.d)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def edit(self, name, fn):
        p = os.path.join(self.d, name)
        obj = json.load(open(p, encoding="utf-8"))
        fn(obj)
        json.dump(obj, open(p, "w", encoding="utf-8"), indent=1, sort_keys=True)

    def errors(self, paths=False):
        return V.validate(self.d, REPO, check_paths=paths and HAVE_BASELINE)

    def assertFails(self, needle, paths=False):
        errs = self.errors(paths)
        self.assertTrue(any(needle in e for e in errs), "expected %r in %r" % (needle, errs[:5]))


class Positive(unittest.TestCase):
    def test_committed_evidence_validates(self):
        self.assertEqual(V.validate(OUT, REPO, check_paths=HAVE_BASELINE), [])

    def test_exactly_40_modules_and_261_screens_cross_checked_against_the_registers(self):
        mods = json.load(open(os.path.join(REPO, "registers", "canonical-modules.json"), encoding="utf-8"))["module_register"]
        scr = json.load(open(os.path.join(REPO, "registers", "canonical-screens.json"), encoding="utf-8"))["screen_register"]
        inv = json.load(open(os.path.join(OUT, "module-inventory.json"), encoding="utf-8"))
        cov = json.load(open(os.path.join(OUT, "screen-coverage.json"), encoding="utf-8"))
        self.assertEqual([m["id"] for m in mods], [r["id"] for r in inv["modules"]])
        self.assertEqual(sorted(s["id"] for s in scr), sorted(p["id"] for p in cov["perScreen"]))
        self.assertEqual(len(inv["modules"]), 40)
        self.assertEqual(len(cov["perScreen"]), 261)

    def test_unknown_stays_null(self):
        inv = json.load(open(os.path.join(OUT, "module-inventory.json"), encoding="utf-8"))
        self.assertIsNone(inv["confirmedUnbuiltModules"])
        self.assertIsNone(inv["confirmedFullyBuiltModules"])
        self.assertIsNone(inv["productionReadyPercent"])
        self.assertTrue(all(r["clinicalAccepted"] is False and r["productionIntegrated"] == "not_verified" for r in inv["modules"]))

    def test_pull_request_snapshot_is_complete_and_consistent(self):
        man = json.load(open(os.path.join(OUT, "manifest.json"), encoding="utf-8"))
        snap = json.load(open(os.path.join(OUT, "inputs", "pr-snapshot.json"), encoding="utf-8"))["prs"]
        self.assertEqual(len(man["pullRequests"]), len(snap))
        self.assertEqual(sum(man["pullRequestSummary"].values()), len(snap))
        for p in man["pullRequests"]:
            if p["state"] in ("MERGED", "CLOSED") and p["headObjectPresent"]:
                self.assertTrue(p["headReachableFromBaseline"], "PR %s head is not in the baseline" % p["number"])

    def test_active_m38_m39_m40_are_claimed_not_changed(self):
        ev = {m["id"]: m for m in json.load(open(os.path.join(OUT, "module-evidence.json"), encoding="utf-8"))["modules"]}
        for mid in ("M38", "M39", "M40"):
            self.assertNotEqual(ev[mid]["state"], "routes_registered_no_module_source_located")

    def test_generated_matrix_document_is_in_sync(self):
        import render_matrix
        cur = open(os.path.join(REPO, "docs", "program", "reconciliation", "MODULE_EVIDENCE_MATRIX.md"), encoding="utf-8").read()
        self.assertEqual(cur, render_matrix.render(REPO), "run tools/reconciliation/render_matrix.py")

    def test_report_backlog_ids_exist(self):
        import re
        rep = open(os.path.join(REPO, "docs", "program", "reconciliation", "RECONCILIATION_REPORT.md"), encoding="utf-8").read()
        ids = {i["id"] for i in json.load(open(os.path.join(OUT, "backlog.json"), encoding="utf-8"))["items"]}
        for m in re.findall(r"BL-\d{3}", rep):
            self.assertIn(m, ids)

    def test_pr_touches_only_reconciliation_paths(self):
        if not os.path.isdir(os.path.join(REPO, ".git")) or not HAVE_BASELINE:
            self.skipTest("needs git history")
        base = json.load(open(os.path.join(OUT, "manifest.json"), encoding="utf-8"))["baselineSha"]
        out = subprocess.run(["git", "-C", REPO, "diff", "--name-only", base, "HEAD"], capture_output=True, text=True).stdout.split()
        allowed = ("docs/program/reconciliation/", "registers/reconciliation/", "tests/reconciliation/", "tools/reconciliation/", ".github/workflows/reconciliation-validation.yml")
        bad = [p for p in out if not p.startswith(allowed)]
        self.assertEqual(bad, [], "paths outside the permitted reconciliation scope")

    @unittest.skipUnless(os.environ.get("RECON_RESCAN") == "1" and HAVE_BASELINE, "set RECON_RESCAN=1 with full history")
    def test_regeneration_is_byte_identical(self):
        self.assertEqual(subprocess.run([sys.executable, os.path.join(REPO, "tools", "reconciliation", "validate_reconciliation.py"), "--rescan"], cwd=REPO).returncode, 0)


class Negative(Mutable):
    def test_missing_module_is_rejected(self):
        self.edit("module-inventory.json", lambda o: o["modules"].pop(5))
        self.assertFails("M01..M40 exactly once")

    def test_duplicate_module_is_rejected(self):
        self.edit("module-evidence.json", lambda o: o["modules"].append(copy.deepcopy(o["modules"][0])))
        self.assertFails("exactly once")

    def test_fabricated_completion_state_is_rejected(self):
        def f(o):
            o["modules"][0]["state"] = "production_ready_accepted"
        self.edit("module-inventory.json", f)
        self.assertFails("unrecognised state")

    def test_verified_or_confirmed_absent_classification_needs_human_review(self):
        for cls in ("implemented_and_verified", "confirmed_not_implemented"):
            def f(o, cls=cls):
                o["modules"][0]["classification"] = cls
            self.setUp()
            self.edit("module-inventory.json", f)
            self.assertFails("without a recorded human review")

    def test_clinical_acceptance_claim_is_rejected(self):
        def f(o):
            o["modules"][3]["clinicalAccepted"] = True
        self.edit("module-inventory.json", f)
        self.assertFails("clinicalAccepted must be false")

    def test_production_integration_claim_is_rejected(self):
        def f(o):
            o["modules"][3]["productionIntegrated"] = "verified"
        self.edit("module-inventory.json", f)
        self.assertFails("productionIntegrated")

    def test_confirmed_unbuilt_count_requires_a_full_human_audit(self):
        def f(o):
            o["confirmedUnbuiltModules"] = 14
        self.edit("module-inventory.json", f)
        self.assertFails("must be null until all 40")

    def test_engineering_slice_without_evidence_is_rejected(self):
        def f(o):
            m = next(x for x in o["modules"] if x["state"] == "engineering_slice_service_db_contract_tests")
            m["owned"]["migrations"] = []
        self.edit("module-evidence.json", f)
        self.assertFails("without service+migration+contract+tests evidence")

    def test_source_claim_without_paths_is_rejected(self):
        def f(o):
            m = next(x for x in o["modules"] if x["state"] == "partial_source_located")
            m["owned"]["pathCount"] = 0
            m["owned"]["exemplars"] = []
        self.edit("module-evidence.json", f)
        self.assertFails("lists no evidence paths")

    def test_no_source_state_cannot_list_paths(self):
        def f(o):
            m = next(x for x in o["modules"] if x["state"] == "routes_registered_no_module_source_located")
            m["owned"]["pathCount"] = 3
        self.edit("module-evidence.json", f)
        self.assertFails("says no source located")

    @unittest.skipUnless(HAVE_BASELINE, "needs the baseline commit")
    def test_bogus_source_path_is_rejected(self):
        def f(o):
            m = next(x for x in o["modules"] if x["owned"]["exemplars"])
            m["owned"]["exemplars"].append("services/does-not-exist/Fabricated.java")
        self.edit("module-evidence.json", f)
        self.assertFails("does not exist at baseline", paths=True)

    def test_screen_owned_by_two_modules_is_rejected(self):
        def f(o):
            a, b = o["modules"][0], o["modules"][1]
            sid = next(iter(a["screens"]["perScreen"]))
            b["screens"]["perScreen"][sid] = a["screens"]["perScreen"][sid]
        self.edit("module-evidence.json", f)
        self.assertFails("is owned by two modules")

    def test_duplicate_screen_id_is_rejected(self):
        def f(o):
            o["screens"].append(copy.deepcopy(o["screens"][0]))
        self.edit("canonical-inventory.json", f)
        self.assertFails("261 unique ids")

    def test_duplicate_static_entrypoint_is_rejected(self):
        def f(o):
            o["duplicateStaticEntrypoints"] = ["M01-S01"]
        self.edit("screen-coverage.json", f)
        self.assertFails("duplicate static entrypoints")

    def test_percentage_fields_are_rejected(self):
        def f(o):
            o["modules"][0]["completionPercent"] = 61
        self.edit("module-inventory.json", f)
        self.assertFails("non-null percentage")

    def test_confirmed_backlog_item_needs_evidence(self):
        def f(o):
            o["items"][0]["confirmed"] = True
            o["items"][0]["evidence"] = []
        self.edit("backlog.json", f)
        self.assertFails("confirmed without evidence")

    def test_backlog_cannot_propose_rebuilding_a_module(self):
        def f(o):
            o["items"][0]["title"] = "Rebuild M07 from scratch"
        self.edit("backlog.json", f)
        self.assertFails("rebuilding a module")

    def test_claimed_active_module_cannot_be_no_source(self):
        def f(o):
            m = next(x for x in o["modules"] if x["id"] == "M39")
            m["state"] = "routes_registered_no_module_source_located"
            m["owned"]["pathCount"] = 0
        self.edit("module-evidence.json", f)
        self.assertFails("claimed/active evidence")

    def test_ci_claim_requires_a_url_and_sha(self):
        def f(o):
            m = next(x for x in o["modules"] if x["ci"])
            m["ci"][0]["url"] = ""
        self.edit("module-evidence.json", f)
        self.assertFails("CI evidence needs url and headSha")

    def test_tampered_pinned_input_is_rejected(self):
        p = os.path.join(REPO, "registers", "reconciliation", "inputs", "pr-snapshot.json")
        orig = open(p, "rb").read()
        self.assertEqual(V.validate(self.d, REPO, check_paths=False), [])
        man = json.load(open(os.path.join(self.d, "manifest.json"), encoding="utf-8"))
        man["inputs"]["prs"]["sha256"] = hashlib.sha256(orig.replace(b"\r\n", b"\n") + b"x").hexdigest()
        json.dump(man, open(os.path.join(self.d, "manifest.json"), "w", encoding="utf-8"), indent=1)
        self.assertFails("pinned input changed")


if __name__ == "__main__":
    unittest.main()
