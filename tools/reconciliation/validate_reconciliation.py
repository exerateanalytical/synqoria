#!/usr/bin/env python3
"""Offline validator for registers/reconciliation/** (Issue #60, package 29).

Fails (exit 1) when the evidence is internally inconsistent or overclaims. It is deterministic and never touches the network.
Checks: 40 module ids exactly once; 261 screens exactly once with one owner; no fabricated completion/acceptance; every claim
carries evidence; every cited source path exists at the baseline commit; pinned inputs match their recorded hashes; optional
byte-identical regeneration.

usage: validate_reconciliation.py [--dir registers/reconciliation] [--repo .] [--rescan]
"""
import argparse, collections, hashlib, json, os, re, subprocess, sys, tempfile

ALLOWED_STATES = {"routes_registered_no_module_source_located", "partial_source_located", "engineering_slice_service_db_contract_tests"}
FORBIDDEN_STATE_WORDS = re.compile(r"(?i)(accepted|certified|production[-_ ]ready|production[-_ ]integrated|fully[-_ ]built|complete)")
REQUIRED = ["manifest.json", "canonical-inventory.json", "historical-inventory.json", "provenance.json", "duplicate-fingerprints.json", "module-evidence.json",
            "screen-coverage.json", "contracts-events-security.json", "module-inventory.json", "backlog.json"]


def load(d, n):
    with open(os.path.join(d, n), encoding="utf-8") as f:
        return json.load(f)


def tracked(repo, sha):
    out = subprocess.check_output(["git", "-C", repo, "ls-tree", "-r", "--name-only", "-z", sha]).decode("utf-8", "replace")
    return set(x for x in out.split("\0") if x)


def exists(paths, p):
    return p in paths or any(x.startswith(p.rstrip("/") + "/") for x in paths)


def validate(d, repo, check_paths=True):
    errs = []
    def err(m):
        errs.append(m)
    for n in REQUIRED:
        if not os.path.isfile(os.path.join(d, n)):
            err("missing output file " + n)
    if errs:
        return errs
    man, canon, inv, ev, scr, back, hist, dup, con = (load(d, n) for n in ("manifest.json", "canonical-inventory.json", "module-inventory.json", "module-evidence.json", "screen-coverage.json",
                                                                         "backlog.json", "historical-inventory.json", "duplicate-fingerprints.json", "contracts-events-security.json"))
    # 1. exactly 40 module ids, once each, in canonical order M01..M40
    want = ["M%02d" % i for i in range(1, 41)]
    for name, rows in (("canonical-inventory", canon["modules"]), ("module-inventory", inv["modules"]), ("module-evidence", ev["modules"])):
        ids = [r["id"] for r in rows]
        if ids != want:
            c = collections.Counter(ids)
            err("%s must list M01..M40 exactly once (missing=%s duplicated=%s)" % (name, sorted(set(want) - set(ids)), sorted(k for k, v in c.items() if v > 1)))
    # 2. 261 screens exactly once, one owning module, unique routes
    sids = [s["id"] for s in canon["screens"]]
    if len(sids) != 261 or len(set(sids)) != 261:
        err("canonical screens must be 261 unique ids (got %d / %d unique)" % (len(sids), len(set(sids))))
    routes = [s["route"] for s in canon["screens"]]
    if len(set(routes)) != len(routes) or any(r is None for r in routes):
        err("screen routes must be unique and present")
    owner = {}
    for m in ev["modules"]:
        for sid in m["screens"]["perScreen"]:
            if sid in owner:
                err("screen %s is owned by two modules (%s, %s)" % (sid, owner[sid], m["id"]))
            owner[sid] = m["id"]
    sh_ids = {s["id"] for s in canon["screens"] if s["module"] == "SHARED"}
    if set(owner) | sh_ids != set(sids):
        err("module-owned screens plus SHARED must equal the 261 canonical screens")
    for s in canon["screens"]:
        if s["module"] != "SHARED" and owner.get(s["id"]) != s["module"]:
            err("screen %s belongs to %s in the register but is evidenced under %s" % (s["id"], s["module"], owner.get(s["id"])))
    if scr["screenCount"] != 261 or len(scr["perScreen"]) != 261:
        err("screen-coverage must cover 261 screens")
    if scr["duplicateStaticEntrypoints"]:
        err("duplicate static entrypoints for screens: %s" % scr["duplicateStaticEntrypoints"])
    # 3. no fabricated completion / acceptance
    for r in inv["modules"]:
        if r["state"] not in ALLOWED_STATES:
            err("%s has unrecognised state %r" % (r["id"], r["state"]))
        if FORBIDDEN_STATE_WORDS.search(r["state"]):
            err("%s state claims completion/acceptance: %s" % (r["id"], r["state"]))
        if r.get("clinicalAccepted") is not False:
            err("%s clinicalAccepted must be false" % r["id"])
        if r.get("productionIntegrated") != "not_verified":
            err("%s productionIntegrated must be 'not_verified'" % r["id"])
        if r.get("humanReviewed") is not False and not r.get("reviewEvidence"):
            err("%s claims humanReviewed without reviewEvidence" % r["id"])
    for k in ("confirmedUnbuiltModules", "confirmedFullyBuiltModules", "productionReadyPercent"):
        if inv.get(k) is not None and not all(r.get("humanReviewed") for r in inv["modules"]):
            err("%s must be null until all 40 modules are human-audited" % k)
    if inv["stateCounts"] != dict(sorted(collections.Counter(r["state"] for r in inv["modules"]).items())):
        err("stateCounts do not match module rows")
    # 4. every claim has evidence
    for m in ev["modules"]:
        o = m["owned"]
        if m["state"] != "routes_registered_no_module_source_located":
            if o["pathCount"] <= 0 or not o["exemplars"]:
                err("%s claims source but lists no evidence paths" % m["id"])
        if m["state"] == "engineering_slice_service_db_contract_tests":
            p = m["present"]
            if not (p["serviceCode"] and p["persistence"] and p["contract"] and p["serviceTests"] and o["migrations"] and o["contracts"] and o["tests"]):
                err("%s claims an engineering slice without service+migration+contract+tests evidence" % m["id"])
        if m["state"] == "routes_registered_no_module_source_located" and o["pathCount"] != 0:
            err("%s says no source located but lists owned paths" % m["id"])
        for c in m["ci"]:
            if not c.get("url") or not c.get("headSha"):
                err("%s CI evidence needs url and headSha" % m["id"])
        if m["id"] in ("M38", "M39", "M40") and m["state"] == "routes_registered_no_module_source_located":
            err("%s is claimed/active evidence and cannot be 'no source'" % m["id"])
    for b in back["items"]:
        if b["confirmed"] and not b["evidence"]:
            err("backlog %s is marked confirmed without evidence" % b["id"])
        if re.search(r"(?i)\b(rebuild|regenerate|re-?implement)\b", b["title"]):
            err("backlog %s proposes rebuilding a module" % b["id"])
    ranks = [b["rank"] for b in back["items"]]
    if ranks != list(range(1, len(ranks) + 1)):
        err("backlog ranks must be 1..n in order")
    # percentages / counts that imply completion must not be present
    def walk(x, path=""):
        if isinstance(x, dict):
            for k, v in x.items():
                if re.search(r"(?i)percent|pct", k) and v is not None:
                    err("non-null percentage field at %s/%s" % (path, k))
                walk(v, path + "/" + k)
        elif isinstance(x, list):
            for i, v in enumerate(x):
                walk(v, path + "/%d" % i)
    for n in REQUIRED:
        walk(load(d, n), n)
    # 5. inputs pinned
    for k, v in man["inputs"].items():
        p = os.path.join(repo, v["path"])
        if not os.path.isfile(p):
            err("pinned input missing: " + v["path"])
        elif hashlib.sha256(open(p, "rb").read().replace(b"\r\n", b"\n")).hexdigest() != v["sha256"]:
            err("pinned input changed since the scan: " + v["path"])
    # 6. paths exist at the baseline
    if check_paths:
        sha = man["baselineSha"]
        try:
            paths = tracked(repo, sha)
        except subprocess.CalledProcessError:
            err("baseline commit %s is not available in this checkout (fetch full history)" % sha)
            return errs
        cited = set()
        for m in ev["modules"]:
            o = m["owned"]
            cited |= set(o["exemplars"]) | set(o["migrations"]) | set(o["contracts"]) | set(o["tests"]) | set(o["webPaths"]) | set(o["anchorPaths"]) | set(m["docs"])
            for a in m["anchors"]:
                cited.add(a["citation"])
            for v in m["screens"]["perScreen"].values():
                cited |= set(v["implementationReferences"])
            for c in m["ci"]:
                cited.add(c["file"])
        for g in dup["identicalContentGroups"]:
            cited |= set(g)
        for p in scr["perScreen"]:
            cited |= set(p["staticEntrypoints"]) | set(p["dynamicEntrypoints"])
        for svc in con["services"]:
            if not exists(paths, svc):
                err("service path does not exist at baseline: " + svc)
        for p in sorted(cited):
            if p not in paths:
                err("evidence path does not exist at baseline %s: %s" % (sha[:8], p))
    return errs


def first_diff(a, b, path=""):
    if type(a) != type(b):
        return path or "/"
    if isinstance(a, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a or k not in b:
                return path + "/" + k
            r = first_diff(a[k], b[k], path + "/" + k)
            if r:
                return r
        return ""
    if isinstance(a, list):
        if len(a) != len(b):
            return path + " (length %d vs %d)" % (len(a), len(b))
        for i, (x, y) in enumerate(zip(a, b)):
            r = first_diff(x, y, path + "/%d" % i)
            if r:
                return r
        return ""
    return "" if a == b else path + " (%r vs %r)" % (str(a)[:80], str(b)[:80])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="registers/reconciliation")
    ap.add_argument("--repo", default=".")
    ap.add_argument("--rescan", action="store_true", help="regenerate at the recorded baseline and require byte-identical output")
    ap.add_argument("--no-paths", action="store_true")
    a = ap.parse_args()
    repo = os.path.abspath(a.repo)
    d = os.path.join(repo, a.dir)
    errs = validate(d, repo, check_paths=not a.no_paths)
    if a.rescan and not errs:
        sha = load(d, "manifest.json")["baselineSha"]
        with tempfile.TemporaryDirectory() as t:
            subprocess.check_call([sys.executable, os.path.join(repo, "tools", "reconciliation", "forensic_scan.py"), "--baseline", sha, "--repo", repo, "--out", os.path.relpath(t, repo) if t.startswith(repo) else t],
                                  stdout=subprocess.DEVNULL)
            for n in REQUIRED:
                if open(os.path.join(t, n), "rb").read() != open(os.path.join(d, n), "rb").read():
                    errs.append("rescan differs from committed output: " + n + " at " + first_diff(load(t, n), load(d, n)))
    for e in errs:
        print("FAIL:", e)
    print("reconciliation validation: %s (%d problem%s)" % ("FAILED" if errs else "ok", len(errs), "" if len(errs) == 1 else "s"))
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main())
