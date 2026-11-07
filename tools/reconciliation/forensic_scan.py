#!/usr/bin/env python3
"""SYNQORIA M01-M37 forensic reconciliation scanner (Issue #60).

Deterministic and OFFLINE: reads only the git object database at a pinned baseline commit plus the pinned inputs in
registers/reconciliation/inputs/. It never calls the network and writes no timestamps, so the same baseline always
produces byte-identical output. Findings are INVENTORY evidence (where source lives, what it contains, what is missing
from that source); they are never acceptance, production integration or certification. Unknown stays null.

usage: forensic_scan.py --baseline <sha> [--out registers/reconciliation] [--repo .]
"""
import argparse, collections, hashlib, json, os, re, subprocess, sys

SELF_PREFIXES = ("registers/reconciliation/", "docs/program/reconciliation/", "tests/reconciliation/", "tools/reconciliation/")
MODULE_RE = re.compile(r"^M(\d{2})$")
SCREEN_RE = r"\b(G|M[0-9]{2})-S[0-9]{2}\b"
IMPL_CATS = {"service-main", "service-db", "contract", "web-api", "web-route", "web-component", "web-lib", "package"}
TEST_CATS = {"service-test", "test"}
MENTION_SCOPES = ["services", "contracts", "apps", "packages", "tests", "tools", ".github"]
GENERIC_CLASS_NAMES = {"Api", "Db", "Platform", "Application", "Domain", "Errors", "Security", "Store", "Actor", "package-info", "SyncService"}


class Repo:
    def __init__(self, root, sha):
        self.root = root
        self.sha = subprocess.check_output(["git", "-C", root, "rev-parse", "--verify", sha + "^{commit}"], text=True).strip()
        self._files = None

    def git(self, *args, check=True):
        r = subprocess.run(["git", "-C", self.root, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
        if check and r.returncode not in (0, 1):
            raise RuntimeError("git %s failed: %s" % (" ".join(args), r.stderr.strip()))
        return r.stdout

    def files(self):
        if self._files is None:
            out = subprocess.check_output(["git", "-C", self.root, "ls-tree", "-r", "-z", "--long", self.sha])
            fs = {}
            for rec in out.decode("utf-8", "replace").split("\0"):
                if not rec:
                    continue
                meta, path = rec.split("\t", 1)
                _, _, blob, size = meta.split()
                if path.startswith(SELF_PREFIXES):
                    continue
                fs[path] = (blob, 0 if size == "-" else int(size))
            self._files = fs
        return self._files

    def show(self, path):
        return subprocess.check_output(["git", "-C", self.root, "show", "%s:%s" % (self.sha, path)]).decode("utf-8", "replace")

    def grep(self, pattern, scopes, files_only=True, word=False):
        args = ["grep", "-I", "-E", "-n" if not files_only else "-l"]
        if not files_only:
            args = ["grep", "-I", "-E", "-o", "-n"]
        args += [pattern, self.sha, "--"] + scopes
        out = self.git(*args, check=False)
        pre = self.sha + ":"
        res = []
        for line in out.splitlines():
            if line.startswith(pre):
                res.append(line[len(pre):])
        return res


def cat(p):
    if p.startswith("services/"):
        if "/src/test/" in p:
            return "service-test"
        if "/src/main/" in p:
            return "service-main"
        if "/db/" in p:
            return "service-db"
        return "service-other"
    if p.startswith("contracts/"):
        return "contract"
    if p.startswith("apps/web/app/ui/"):
        return "web-catalog-preview"
    if p.startswith("apps/web/app/api/"):
        return "web-api"
    if p.startswith("apps/web/app/"):
        return "web-route"
    if p.startswith("apps/web/components/"):
        return "web-component"
    if p.startswith("apps/web/lib/"):
        return "web-lib"
    if p.startswith("apps/web/"):
        return "web-other"
    if p.startswith("packages/ux-core/"):
        return "ux-core"
    if p.startswith("packages/"):
        return "package"
    if p.startswith("tests/"):
        return "test"
    if p.startswith("tools/"):
        return "tool"
    if p.startswith(".github/workflows/"):
        return "workflow"
    if p.startswith(("docs/archive/", "docs/recovery/")):
        return "doc-archive"
    if p.startswith("docs/"):
        return "doc"
    if p.startswith("registers/"):
        return "register"
    if p.startswith("evidence/"):
        return "evidence"
    return "other"


def service_of(p):
    parts = p.split("/")
    return "/".join(parts[:2]) if parts[0] == "services" and len(parts) > 2 else None


def jdump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=1, sort_keys=True, ensure_ascii=False)
        f.write("\n")


def sha256_text(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def under(path, prefix):
    return path == prefix or path.startswith(prefix.rstrip("/") + "/")


# ---------------------------------------------------------------- packages 01-05
def pkg01_manifest(R, inputs):
    prs = inputs["prs"]["prs"]
    branches = []
    for line in R.git("for-each-ref", "--format=%(refname)%09%(objectname)", "refs/remotes/origin").splitlines():
        ref, sha = line.split("\t")
        name = ref[len("refs/remotes/origin/"):]
        if name == "HEAD":
            continue
        anc = subprocess.run(["git", "-C", R.root, "merge-base", "--is-ancestor", sha, R.sha]).returncode == 0
        ahead = int(R.git("rev-list", "--count", "%s..%s" % (R.sha, sha)).strip() or 0) if not anc else 0
        branches.append({"name": name, "tip": sha, "reachableFromBaseline": anc, "commitsNotInBaseline": ahead})
    branches.sort(key=lambda b: b["name"])
    pr_rows = []
    for p in prs:
        have = subprocess.run(["git", "-C", R.root, "cat-file", "-e", p["headSha"] + "^{commit}"], capture_output=True).returncode == 0
        anc = have and subprocess.run(["git", "-C", R.root, "merge-base", "--is-ancestor", p["headSha"], R.sha]).returncode == 0
        pr_rows.append({"number": p["number"], "state": p["state"], "title": p["title"], "headRef": p["headRef"], "headSha": p["headSha"], "headObjectPresent": have,
                        "headReachableFromBaseline": anc, "mergeCommit": p["mergeCommit"], "url": p["url"]})
    ncommits = int(R.git("rev-list", "--count", R.sha).strip())
    files = R.files()
    return {
        "schemaVersion": "synqoria.reconciliation.manifest.v1",
        "baselineSha": R.sha,
        "baselineCommitCount": ncommits,
        "trackedFileCount": len(files),
        "trackedBytes": sum(s for _, s in files.values()),
        "inputs": {k: {"path": v["path"], "sha256": v["sha256"]} for k, v in sorted(inputs["meta"].items())},
        "pullRequests": pr_rows,
        "pullRequestSummary": dict(sorted(collections.Counter(p["state"] for p in pr_rows).items())),
        "openPullRequestsNotInBaseline": [p["number"] for p in pr_rows if p["state"] == "OPEN" and not p["headReachableFromBaseline"]],
        "remoteBranches": branches,
        "branchSummary": {"total": len(branches), "reachableFromBaseline": sum(1 for b in branches if b["reachableFromBaseline"]),
                          "notReachable": sorted(b["name"] for b in branches if not b["reachableFromBaseline"])},
        "lineageStatement": "A closed or merged PR head that is reachable from the baseline has its content in main; no unmerged historical branch content was found outside the listed notReachable branches.",
        "countingPolicy": "inventory evidence only; engineering pass != production integration != hospital acceptance != certification; unknown stays null",
    }


def pkg02_canonical(R):
    cm = json.loads(R.show("registers/canonical-modules.json"))
    cs = json.loads(R.show("registers/canonical-screens.json"))
    sr = json.loads(R.show("registers/screen-routes.json"))
    mods = [{"id": m["id"], "name": m["name_and_owned_records"], "owner": m["owner_and_deployment"], "wave": m["wave"]} for m in cm["module_register"]]
    scr = {s["screen_id"]: s for s in sr["screens"]}
    screens = []
    for s in cs["screen_register"]:
        r = scr.get(s["id"])
        screens.append({"id": s["id"], "module": s["module"], "name": s["name"], "layout": s["layout"], "registerCodeStatus": s["code_status"],
                        "route": r["route"] if r else None, "routeStatus": r["implementation_status"] if r else None})
    ids = [s["id"] for s in screens]
    return {
        "schemaVersion": "synqoria.reconciliation.canonical.v1",
        "sources": {"modules": {"path": "registers/canonical-modules.json", "blob": R.files()["registers/canonical-modules.json"][0]},
                    "screens": {"path": "registers/canonical-screens.json", "blob": R.files()["registers/canonical-screens.json"][0]},
                    "routes": {"path": "registers/screen-routes.json", "blob": R.files()["registers/screen-routes.json"][0]}},
        "moduleCount": len(mods), "screenCount": len(screens),
        "modules": mods, "screens": screens,
        "integrity": {"moduleIdsUnique": len({m["id"] for m in mods}) == len(mods), "screenIdsUnique": len(set(ids)) == len(ids),
                      "everyScreenHasRoute": all(s["route"] for s in screens),
                      "screensByModule": dict(sorted(collections.Counter(s["module"] for s in screens).items()))},
    }


def pkg03_historical(R, manifest):
    files = R.files()
    groups = collections.defaultdict(list)
    for p in files:
        c = cat(p)
        if c in ("doc-archive", "evidence"):
            groups[p.split("/")[0] + "/" + p.split("/")[1] if p.count("/") >= 1 else p].append(p)
    arch = {k: {"files": len(v), "bytes": sum(files[x][1] for x in v)} for k, v in sorted(groups.items())}
    status_docs = sorted(p for p in files if re.search(r"(^|/)(SYNQORIA_)?STATUS_v[0-9.]+.*\.md$", p))
    zips = sorted(p for p in files if re.search(r"\.(zip|tar|tgz|gz|7z)$", p))
    handoffs = sorted(p for p in files if p.startswith("docs/program/") and re.search(r"(ENGINEERING|HANDOFF|REVIEW|RECONCILIATION|handoffs/)", p))
    eng = sorted(p for p in files if p.startswith("docs/engineering/"))
    batch_regs = sorted(p for p in files if re.match(r"registers/v\d+.*\.json$", p) or p.startswith("registers/v067/"))
    return {
        "schemaVersion": "synqoria.reconciliation.historical.v1",
        "archiveAndEvidenceGroups": arch,
        "statusCheckpointDocuments": status_docs,
        "binaryArchivesInRepository": zips,
        "binaryArchiveNote": "SYNQORIA_Baseline_v0.1.0.zip is a seed archive; its contents were not unpacked or attributed (unknown, not unbuilt)" if any("Baseline_v0.1.0" in z for z in zips) else None,
        "engineeringAndHandoffDocuments": handoffs,
        "engineeringNotes": eng,
        "batchRegisters": batch_regs,
        "quarantinedOrRecoveredSources": sorted(p for p in files if p.startswith(("docs/recovery/", "docs/archive/")) and not p.endswith((".json", ".log", ".txt", ".md"))),
        "prLineage": {"mergedPrs": [p["number"] for p in manifest["pullRequests"] if p["state"] == "MERGED"],
                      "closedUnmergedPrsWithHeadInBaseline": [p["number"] for p in manifest["pullRequests"] if p["state"] == "CLOSED" and p["headReachableFromBaseline"]],
                      "closedUnmergedPrsWithHeadNOTInBaseline": [p["number"] for p in manifest["pullRequests"] if p["state"] == "CLOSED" and not p["headReachableFromBaseline"]]},
        "inaccessibleHistory": "worktrees outside this repository, unpushed local branches and PR review comments were not accessible to this scan and are not claimed",
    }


TABLE_RE = re.compile(r"(?is)\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z_][a-z0-9_.]*)")
CLASS_RE = re.compile(r"(?m)^\s*(?:public\s+|final\s+|abstract\s+)*(?:class|interface|record|enum)\s+([A-Z][A-Za-z0-9_]*)")


def pkg04_provenance(R):
    files = R.files()
    svcs = collections.defaultdict(lambda: {"javaMain": 0, "javaTest": 0, "migrations": [], "tables": set(), "classes": set(), "httpRouteFiles": 0, "openapi": []})
    for p in sorted(files):
        s = service_of(p)
        if not s:
            continue
        d = svcs[s]
        c = cat(p)
        if p.endswith(".java"):
            if c == "service-main":
                d["javaMain"] += 1
                d["classes"].add(os.path.basename(p)[:-5])
            elif c == "service-test":
                d["javaTest"] += 1
        if p.endswith(".sql") and "/db/" in p:
            d["migrations"].append(p)
            for t in TABLE_RE.findall(R.show(p)):
                d["tables"].add(t.lower())
    http_hits = set(R.grep(r"@(Get|Post|Put|Patch|Delete|Request)Mapping|createContext\(|HttpHandler|RouterFunction|server\.createContext", ["services"]))
    for p in http_hits:
        s = service_of(p)
        if s and cat(p) == "service-main":
            svcs[s]["httpRouteFiles"] += 1
    contracts = {}
    for p in sorted(files):
        if p.startswith("contracts/openapi/") and p.endswith((".yaml", ".json")):
            txt = R.show(p)
            ops = len(re.findall(r"(?m)^\s*(get|post|put|patch|delete):\s*$", txt)) if p.endswith(".yaml") else len(re.findall(r'"(get|post|put|patch|delete)"\s*:', txt))
            paths = len(re.findall(r"(?m)^  /[^\s:]*:\s*$", txt)) if p.endswith(".yaml") else len(re.findall(r'^\s{4}"/[^"]+"\s*:', txt, re.M))
            contracts[p] = {"operations": ops, "paths": paths, "blob": files[p][0]}
    return {
        "schemaVersion": "synqoria.reconciliation.provenance.v1",
        "services": {k: {"javaMainFiles": v["javaMain"], "javaTestFiles": v["javaTest"], "httpEntrypointFiles": v["httpRouteFiles"], "migrations": v["migrations"],
                         "tables": sorted(v["tables"]), "classCount": len(v["classes"])} for k, v in sorted(svcs.items())},
        "openapiContracts": contracts,
        "eventContracts": sorted(p for p in files if p.startswith("contracts/events/")),
        "method": "paths from git ls-tree at the baseline; tables from CREATE TABLE statements; HTTP entrypoints from handler/mapping annotations; operations counted from OpenAPI method keys. Counts are structural, not behavioural.",
    }


def pkg05_duplicates(R, prov):
    files = R.files()
    by_blob = collections.defaultdict(list)
    for p, (b, sz) in files.items():
        if sz > 200 and not p.startswith(("evidence/", "package-lock.json")) and not p.endswith(("tsbuildinfo", "package-lock.json")):
            by_blob[b].append(p)
    identical = sorted(sorted(v) for v in by_blob.values() if len(v) > 1)
    tables = collections.defaultdict(set)
    for s, d in prov["services"].items():
        for t in d["tables"]:
            tables[t.split(".")[-1]].add(s)
    table_collisions = {t: sorted(s) for t, s in sorted(tables.items()) if len(s) > 1}
    classes = collections.defaultdict(set)
    for p in files:
        if cat(p) == "service-main" and p.endswith(".java"):
            n = os.path.basename(p)[:-5]
            if n not in GENERIC_CLASS_NAMES:
                classes[n].add(service_of(p))
    class_collisions = {c: sorted(s) for c, s in sorted(classes.items()) if len(s) > 1}
    ops = collections.defaultdict(set)
    for p in files:
        if p.startswith("contracts/openapi/") and p.endswith(".yaml"):
            cur = None
            for line in R.show(p).splitlines():
                m = re.match(r"^  (/[^\s:]*):\s*$", line)
                if m:
                    cur = m.group(1)
                    continue
                m = re.match(r"^    (get|post|put|patch|delete):\s*$", line)
                if m and cur:
                    ops[(m.group(1).upper(), re.sub(r"\{[^}]+\}", "{}", cur))].add(p)
    op_collisions = {"%s %s" % k: sorted(v) for k, v in sorted(ops.items()) if len(v) > 1}
    sr = json.loads(R.show("registers/screen-routes.json"))["screens"]
    routes = collections.Counter(s["route"] for s in sr)
    sids = collections.Counter(s["screen_id"] for s in sr)
    return {
        "schemaVersion": "synqoria.reconciliation.duplicates.v1",
        "method": {
            "identicalContent": "git blob SHA equality across different paths (files > 200 bytes, evidence/ excluded): PROVEN byte-identical copies",
            "tableNameCollision": "same unqualified CREATE TABLE name in more than one service: CANDIDATE overlap (may be intentional per-service tables)",
            "classNameCollision": "same non-generic Java class file name in more than one service: CANDIDATE overlap only",
            "openapiOperationCollision": "same HTTP method and normalised path in more than one OpenAPI file: CANDIDATE overlap (may be intentional versions)",
            "screenIdentity": "duplicate screen ids or routes in registers/screen-routes.json",
        },
        "identicalContentGroups": identical,
        "tableNameCollisions": table_collisions,
        "classNameCollisions": class_collisions,
        "openapiOperationCollisions": op_collisions,
        "screenRegister": {"duplicateScreenIds": sorted(k for k, v in sids.items() if v > 1), "duplicateRoutes": sorted(k for k, v in routes.items() if v > 1)},
        "provenOverlap": [g for g in identical],
        "unprovenOverlapNote": "candidates require human review before any consolidation; none were removed or merged by this scan",
    }


# ---------------------------------------------------------------- modules 06-25 + 28
def load_inputs(root):
    inputs = {"meta": {}}
    base = os.path.join(root, "registers", "reconciliation", "inputs")
    for name, key in (("pr-snapshot.json", "prs"), ("ci-runs.json", "ci"), ("capability-anchors.json", "anchors")):
        p = os.path.join(base, name)
        raw = open(p, "rb").read()
        inputs[key] = json.loads(raw.decode("utf-8"))
        inputs["meta"][key] = {"path": "registers/reconciliation/inputs/" + name, "sha256": hashlib.sha256(raw.replace(b"\r\n", b"\n")).hexdigest()}
    return inputs


def module_evidence(R, inputs, canon, prov):
    files = R.files()
    anchors = inputs["anchors"]
    svc_owner = {s: v["modules"] for s, v in anchors["serviceOwnership"].items()}
    modules = [m["id"] for m in canon["modules"]]
    token_hits = {}
    for mid in modules:
        found = set(R.grep(r"\b%s\b" % mid, MENTION_SCOPES))
        found |= {p for p in files if re.search(r"(?i)(^|[^a-z0-9])%s([^0-9]|$)" % mid, p) and cat(p) not in ("register", "evidence")}
        token_hits[mid] = sorted(p for p in found if p in files)
    docs_hits = {}
    for mid in modules:
        d = set(R.grep(r"\b%s\b" % mid, ["docs/program", "docs/engineering"]))
        d |= {p for p in files if p.startswith(("docs/program/", "docs/engineering/")) and re.search(r"(?i)(^|[^a-z0-9])%s([^0-9]|$)" % mid, p)}
        docs_hits[mid] = sorted(p for p in d if p in files)
    sr = {s["id"]: s for s in canon["screens"]}
    screen_refs = collections.defaultdict(set)
    # screen references: file -> ids
    out = R.git("grep", "-I", "-E", "-o", SCREEN_RE, R.sha, "--", "services", "apps", "packages", "tests", "tools", "contracts", check=False)
    pre = R.sha + ":"
    for line in out.splitlines():
        if not line.startswith(pre):
            continue
        rest = line[len(pre):]
        path, _, sid = rest.rpartition(":")
        # git grep -o prints path:line? without -n it prints path:match
        screen_refs[sid].add(path)
    commits = R.git("log", "--format=%H%x09%s", R.sha).splitlines()
    ci_rows = inputs["ci"]["runs"]
    cm_prev = json.loads(R.show("registers/current-platform-reconciliation.json"))
    prev = {m["id"]: m for m in cm_prev["modules"]}
    msm = {m["id"]: m for m in json.loads(R.show("registers/module-status-matrix.json"))["items"]}
    hospital = json.loads(R.show("registers/hospital-document-template-catalog-v1.json"))["templates"]
    docs_by_mod = collections.Counter(t.get("module") for t in hospital)
    rows = []
    for m in canon["modules"]:
        mid = m["id"]
        owned_svcs = sorted(s for s, mods in svc_owner.items() if mid in mods)
        owned = set()
        for p in files:
            c = cat(p)
            if c in ("register", "evidence", "doc", "doc-archive", "ux-core", "web-catalog-preview"):
                continue
            for s in owned_svcs:
                if under(p, s):
                    owned.add(p)
        anchor_paths = []
        anchor_notes = []
        for a in anchors["pathAnchors"]:
            if a["module"] == mid:
                anchor_notes.append({"citation": a["citation"], "scope": a["scope"]})
                for ap in a["paths"]:
                    for p in files:
                        if under(p, ap):
                            owned.add(p)
                            anchor_paths.append(p)
        pathid = {p for p in token_hits[mid] if re.search(r"(?i)(^|[^a-z0-9])%s([^0-9]|$)" % mid, p) and cat(p) not in ("doc", "doc-archive", "ux-core", "web-catalog-preview")}
        owned |= pathid
        mention = sorted(p for p in token_hits[mid] if p not in owned and cat(p) not in ("doc", "doc-archive"))
        by_cat = collections.Counter(cat(p) for p in owned)
        mention_cat = collections.Counter(cat(p) for p in mention)
        migrations = sorted(p for p in owned if cat(p) == "service-db" and p.endswith(".sql"))
        contracts = sorted(p for p in owned if cat(p) == "contract")
        tests = sorted(p for p in owned if cat(p) in TEST_CATS)
        # tests that reference the module id but live in tests/ count as mentions of the module (indirect evidence)
        indirect_tests = sorted(p for p in mention if cat(p) in TEST_CATS)
        workflows = sorted({p for p in token_hits[mid] if cat(p) == "workflow"} | {p for p in owned if cat(p) == "workflow"})
        web = sorted(p for p in owned if cat(p) in ("web-route", "web-component", "web-lib", "web-api"))
        screens_m = [s for s in canon["screens"] if s["module"] == mid]
        status_count = collections.Counter(s["routeStatus"] for s in screens_m)
        sref = {}
        for s in screens_m:
            refs = sorted(screen_refs.get(s["id"], []))
            impl = [r for r in refs if cat(r) in IMPL_CATS and cat(r) != "web-catalog-preview"]
            sref[s["id"]] = {"routeStatus": s["routeStatus"], "implementationReferences": impl[:6], "referenceCount": len(refs),
                             "evidence": "implementation_reference" if impl else ("catalog_or_test_reference_only" if refs else "route_registered_only")}
        commit_hits = [c.split("\t")[0] for c in commits if re.search(r"\b%s\b" % mid, c.split("\t", 1)[1])]
        pr_hits = [p["number"] for p in inputs["prs"]["prs"] if re.search(r"\b%s\b" % mid, p["title"] + " " + p["headRef"].upper().replace("-", " "))]
        ci = []
        for w in workflows:
            nm = R.show(w)
            m1 = re.search(r"(?m)^name:\s*(.+)$", nm)
            wn = m1.group(1).strip().strip("'\"") if m1 else os.path.basename(w)
            for r in ci_rows:
                if r["workflow"] == wn:
                    ci.append({"workflow": wn, "file": w, "latestRunConclusion": r["conclusion"], "headSha": r["headSha"], "headBranch": r.get("headBranch"), "url": r["url"]})
        tables = sorted({t for s in owned_svcs for t in prov["services"].get(s, {}).get("tables", [])}) if len(owned_svcs) == 1 else None
        has = {"serviceCode": by_cat.get("service-main", 0) > 0, "persistence": bool(migrations), "contract": bool(contracts),
               "serviceTests": by_cat.get("service-test", 0) > 0, "webSource": bool(web), "ciWorkflow": bool(workflows)}
        if not owned:
            state, conf = "routes_registered_no_module_source_located", "low"
            rationale = ["No source path was attributable to this module by id, owned service or curated anchor. This is NOT proof of absence: source may exist under capability names not yet anchored."]
        elif has["serviceCode"] and has["persistence"] and has["contract"] and has["serviceTests"]:
            state, conf = "engineering_slice_service_db_contract_tests", "medium"
            rationale = ["Service code, migrations, an OpenAPI contract and service tests are all attributable by id/ownership. Behaviour, runtime integration and clinical acceptance were not evaluated."]
        else:
            state, conf = "partial_source_located", "low"
            rationale = ["Some module-specific source was attributed but at least one of service code, migrations, contract, service tests is missing from it."]
        if anchor_notes and mid not in ("M33",):
            rationale.append("Attribution relies on curated anchors (see anchors); several slices are quarantined or shared across modules.")
        gaps = [k for k, v in (("serviceCode", has["serviceCode"]), ("persistence", has["persistence"]), ("contract", has["contract"]), ("serviceTests", has["serviceTests"]), ("webSource", has["webSource"]), ("ciWorkflow", has["ciWorkflow"])) if not v]
        pv = prev.get(mid, {})
        rows.append({
            "id": mid, "name": m["name"], "wave": m["wave"], "owner": m["owner"],
            "state": state, "confidence": conf, "reviewMethod": "automated-inventory", "humanReviewed": False,
            "rationale": rationale,
            "owned": {"byCategory": dict(sorted(by_cat.items())), "services": owned_svcs, "migrations": migrations, "contracts": contracts, "tests": tests[:12], "webPaths": web[:12], "anchorPaths": sorted(set(anchor_paths))[:12],
                      "pathCount": len(owned), "exemplars": sorted(owned)[:10]},
            "anchors": anchor_notes,
            "mentions": {"byCategory": dict(sorted(mention_cat.items())), "indirectTests": indirect_tests[:12], "pathCount": len(mention)},
            "present": has, "missingFacets": gaps,
            "tables": tables,
            "screens": {"count": len(screens_m), "byRouteStatus": dict(sorted(status_count.items())), "perScreen": sref},
            "formsAndTemplates": {"hospitalDocumentTemplates": docs_by_mod.get(mid, 0)},
            "docs": docs_hits[mid][:20], "docCount": len(docs_hits[mid]),
            "commits": {"count": len(commit_hits), "newest": commit_hits[:5]}, "pullRequests": sorted(pr_hits),
            "ci": ci,
            "priorRegisters": {"currentPlatformReconciliation": {"codeSignals": pv.get("codeSignals"), "sourceEvidence": pv.get("sourceEvidence")},
                               "moduleStatusMatrix": {"implemented": msm.get(mid, {}).get("implemented"), "tested": msm.get(mid, {}).get("tested")}},
            "productionIntegrated": "not_verified", "clinicalAccepted": False,
        })
    return rows


# ---------------------------------------------------------------- package 26
def next_routes(R):
    routes = []
    for p in R.files():
        if p.startswith("apps/web/app/") and re.search(r"/page\.(tsx|ts|jsx|js)$", p):
            r = "/" + p[len("apps/web/app/"):].rsplit("/page.", 1)[0]
            routes.append((r if r != "/" else "/", p))
    return sorted(routes)


def match_route(route, pats):
    segs = route.strip("/").split("/")
    static, dynamic = [], []
    for pat, file in pats:
        ps = pat.strip("/").split("/") if pat != "/" else [""]
        if len(ps) != len(segs):
            continue
        ok, dyn = True, False
        for a, b in zip(ps, segs):
            if re.fullmatch(r"\[[^\]/]+\]", a):
                dyn = True
            elif a != b:
                ok = False
                break
        if ok:
            (dynamic if dyn else static).append(file)
    return static, dynamic


def pkg26_screens(R, canon, mods):
    pats = next_routes(R)
    sr = json.loads(R.show("registers/screen-routes.json"))["screens"]
    per = []
    for s in sr:
        static, dyn = match_route(s["route"], pats)
        per.append({"id": s["screen_id"], "module": s["module"], "route": s["route"], "routeStatus": s["implementation_status"], "staticEntrypoints": static, "dynamicEntrypoints": dyn,
                    "entrypointKind": "static_page" if static else ("shared_dynamic_page" if dyn else "no_entrypoint_matched")})
    kinds = collections.Counter(p["entrypointKind"] for p in per)
    multi_static = [p["id"] for p in per if len(p["staticEntrypoints"]) > 1]
    # module-level implementation references per screen, duplicates across families
    impl_by_screen = {}
    for m in mods:
        for sid, v in m["screens"]["perScreen"].items():
            impl_by_screen[sid] = v["implementationReferences"]
    def family(p):
        return "/".join(p.split("/")[:4])
    multi_family = {sid: sorted({family(p) for p in refs}) for sid, refs in impl_by_screen.items() if len({family(p) for p in refs}) > 1}
    state_counts = collections.Counter(v["evidence"] for m in mods for v in m["screens"]["perScreen"].values())
    status_vs_evidence = [{"id": sid, "routeStatus": v["routeStatus"], "evidence": v["evidence"]} for m in mods for sid, v in sorted(m["screens"]["perScreen"].items())
                          if (v["routeStatus"] == "route_registered_domain_adapter_missing" and v["evidence"] == "implementation_reference")
                          or (v["routeStatus"] != "route_registered_domain_adapter_missing" and v["evidence"] == "route_registered_only")]
    hosp = json.loads(R.show("registers/hospital-document-template-catalog-v1.json"))
    templates = hosp["templates"]
    tids = collections.Counter(t["id"] for t in templates)
    printables = {}
    for p in sorted(R.files()):
        if p.startswith("apps/web/app/ui/printables/catalog-") and p.endswith(".json"):
            d = json.loads(R.show(p))
            printables[p] = [x.get("id") for x in d] if isinstance(d, list) else [x.get("id") for x in d.get("templates", d.get("items", []))]
    allp = [i for v in printables.values() for i in v if i]
    pcount = collections.Counter(allp)
    return {
        "schemaVersion": "synqoria.reconciliation.screens.v1",
        "screenCount": len(per),
        "entrypointKinds": dict(sorted(kinds.items())),
        "duplicateStaticEntrypoints": multi_static,
        "screensReferencedByImplementationInMultipleFamilies": dict(sorted(multi_family.items())),
        "screenEvidenceStates": dict(sorted(state_counts.items())),
        "registerStatusVersusEvidenceDiscrepancies": status_vs_evidence,
        "perScreen": per,
        "forms": {"fieldLibrary": {"path": "registers/hospital-document-field-library.json", "present": "registers/hospital-document-field-library.json" in R.files()}},
        "reportsAndTemplates": {
            "hospitalDocumentTemplateCatalog": {"count": len(templates), "uniqueIds": len(tids), "duplicateIds": sorted(k for k, v in tids.items() if v > 1),
                                                "byModule": dict(sorted(collections.Counter(t.get("module") for t in templates).items()))},
            "printableCatalogs": {"files": len(printables), "entries": len(allp), "uniqueIds": len(pcount), "duplicateIds": sorted(k for k, v in pcount.items() if v > 1)},
            "crossCatalogIdOverlap": sorted(set(tids) & set(pcount)),
            "note": "templates are print/form definitions, not workflow implementations; counted separately from screens",
        },
    }


# ---------------------------------------------------------------- package 27
def pkg27_contracts_security(R, prov):
    files = R.files()
    rows = {}
    perm_re = re.compile(r'"([a-z][a-z0-9]{1,11}:[a-z][a-z_]{2,40})"')
    for s, d in sorted(prov["services"].items()):
        sql = "\n".join(R.show(p) for p in d["migrations"])
        sqlu = sql.upper()
        main_files = [p for p in files if under(p, s) and cat(p) == "service-main"]
        perms = set()
        outbox_java = inbox_java = 0
        for p in main_files:
            base = os.path.basename(p)
            if "Outbox" in base:
                outbox_java += 1
            if "Inbox" in base:
                inbox_java += 1
            if p.endswith((".java", ".yml")):
                perms |= set(perm_re.findall(R.show(p)))
        rows[s] = {
            "migrationFiles": len(d["migrations"]),
            "rlsEnabledStatements": len(re.findall(r"ENABLE ROW LEVEL SECURITY", sqlu)),
            "rlsForcedStatements": len(re.findall(r"FORCE ROW LEVEL SECURITY", sqlu)),
            "policies": len(re.findall(r"CREATE POLICY", sqlu)),
            "usesCellGuc": "synqoria.cell" in sql, "usesFacilityGuc": "synqoria.facility" in sql,
            "outboxTables": sorted(t for t in d["tables"] if "outbox" in t), "inboxTables": sorted(t for t in d["tables"] if "inbox" in t),
            "auditTables": sorted(t for t in d["tables"] if "audit" in t),
            "outboxJavaFiles": outbox_java, "inboxJavaFiles": inbox_java,
            "permissionStrings": sorted(perms)[:40], "permissionStringCount": len(perms),
            "tableCount": len(d["tables"]),
            "verified": "structural inventory of SQL/Java text only; runtime RLS behaviour, token verification and event delivery were NOT exercised by this scan",
        }
    contracts = {}
    for p in sorted(files):
        if p.startswith("contracts/openapi/"):
            txt = R.show(p)
            contracts[p] = {"declaresBearerSecurity": bool(re.search(r"bearer", txt, re.I)), "declaresOidc": bool(re.search(r"openIdConnect|oauth2|oidc", txt, re.I))}
    events = {}
    for p in sorted(files):
        if p.startswith("contracts/events/"):
            events[p] = {"blob": files[p][0]}
    return {"schemaVersion": "synqoria.reconciliation.contracts.v1", "services": rows, "openapiSecurity": contracts, "eventContracts": events,
            "nonVerifiedStatement": "Event contracts, OIDC permission grammar and RLS posture are described from source text. No contract is claimed verified at runtime; unknowns remain null.",
            "servicesWithoutOutbox": sorted(s for s, r in rows.items() if not r["outboxTables"]),
            "servicesWithoutForcedRls": sorted(s for s, r in rows.items() if r["rlsForcedStatements"] == 0)}


# ---------------------------------------------------------------- package 28 + 30
def pkg28_inventory(mods, canon):
    rows = []
    for m in mods:
        rows.append({"id": m["id"], "name": m["name"], "wave": m["wave"], "state": m["state"], "confidence": m["confidence"], "humanReviewed": m["humanReviewed"],
                     "ownedPathCount": m["owned"]["pathCount"], "services": m["owned"]["services"], "migrationCount": len(m["owned"]["migrations"]), "contractCount": len(m["owned"]["contracts"]),
                     "testPathCount": len(m["owned"]["tests"]), "ciWorkflows": [c["workflow"] for c in m["ci"]], "ciLatestRun": sorted({c["latestRunConclusion"] for c in m["ci"]}),
                     "screenCount": m["screens"]["count"], "missingFacets": m["missingFacets"], "evidencePaths": m["owned"]["exemplars"], "rationale": m["rationale"],
                     "claimedBy": {"M38": "PR #50 (server) / #56 (device client)", "M39": "PR #51 and follow-ups", "M40": "PR #52 and follow-ups"}.get(m["id"]),
                     "productionIntegrated": "not_verified", "clinicalAccepted": False})
    states = collections.Counter(r["state"] for r in rows)
    return {"schemaVersion": "synqoria.reconciliation.inventory.v1", "moduleCount": len(rows), "modules": rows, "stateCounts": dict(sorted(states.items())),
            "confirmedUnbuiltModules": None, "confirmedFullyBuiltModules": None, "productionReadyPercent": None,
            "unbuiltCountRule": "null until every module has a definitive human audit; routes_registered_no_module_source_located is an UNKNOWN, not an unbuilt count"}


def pkg30_backlog(mods, screens, contracts, dup, canon):
    items = []
    n = 0
    def add(title, kind, sev, evidence, deps, owner, scope, accept, confirmed):
        nonlocal n
        n += 1
        items.append({"id": "BL-%03d" % n, "title": title, "kind": kind, "severity": sev, "confirmed": confirmed, "evidence": evidence, "dependsOn": deps, "ownerSuggestion": owner,
                      "boundedPrScope": scope, "acceptance": accept})
    unk = [m for m in mods if m["state"] == "routes_registered_no_module_source_located"]
    for wave in ("A", "B", "C"):
        g = [m["id"] for m in unk if m["wave"] == wave]
        if g:
            add("Human audit of modules with no attributed source (wave %s): %s" % (wave, ", ".join(g)), "audit", {"A": "high", "B": "medium", "C": "low"}[wave],
                [{"modules": g, "finding": "no source attributed by id/ownership/anchor; status UNKNOWN (not proven unbuilt)"}], ["BL-001"] if wave != "A" else [],
                "reconciliation reviewer (not the module builder)", ["docs/program/reconciliation/**", "registers/reconciliation/inputs/capability-anchors.json"],
                "each module has a recorded human audit with source paths or an explicit confirmed-absent finding signed in the evidence matrix", False)
    drift = screens["registerStatusVersusEvidenceDiscrepancies"]
    if drift:
        add("Reconcile screen register statuses with source references (%d screens)" % len(drift), "register-drift", "medium",
            [{"screens": [d["id"] for d in drift][:40], "finding": "register status disagrees with inventory evidence"}], [], "integration lead (owns registers/screen-routes.json)",
            ["registers/screen-routes.json (integration lead only)"], "every listed screen is either re-statused with evidence or the evidence is explained", True)
    for m in mods:
        if m["state"] in ("engineering_slice_service_db_contract_tests", "partial_source_located"):
            miss = [x for x in m["missingFacets"] if x in ("ciWorkflow", "webSource", "contract", "serviceTests", "persistence")]
            if miss and m["id"] not in ("M38", "M39", "M40"):
                add("%s: missing %s in attributed source" % (m["id"], ", ".join(miss)), "facet-gap", "high" if m["wave"] == "A" else "medium",
                    [{"module": m["id"], "attributedPathCount": m["owned"]["pathCount"], "missingFacets": miss, "state": m["state"]}], [], "owner of %s (per ownership register)" % m["id"],
                    ["the module's own paths only"], "the missing facets exist and pass module CI, or the gap is explained as intentionally out of scope", True)
    for s in contracts["servicesWithoutForcedRls"]:
        if contracts["services"][s]["migrationFiles"] > 0:
            add("%s: no FORCE ROW LEVEL SECURITY statement found in migrations" % s, "security-gap", "high",
                [{"service": s, "migrationFiles": contracts["services"][s]["migrationFiles"], "rlsEnabled": contracts["services"][s]["rlsEnabledStatements"]}], [], "service owner + security reviewer",
                [s + "/db/**"], "FORCE RLS policies keyed on cell/facility with a negative cross-tenant test on PostgreSQL", True)
    for s in contracts["servicesWithoutOutbox"]:
        if contracts["services"][s]["tableCount"] > 0:
            add("%s: no outbox table found although it persists data" % s, "event-gap", "medium", [{"service": s, "tableCount": contracts["services"][s]["tableCount"]}], [],
                "service owner", [s + "/db/**"], "transactional outbox or documented reason events are not required", True)
    if dup["identicalContentGroups"]:
        add("Review %d groups of byte-identical files at different paths" % len(dup["identicalContentGroups"]), "duplication", "low", [{"groups": dup["identicalContentGroups"][:10]}], [], "integration lead",
            ["listed duplicate paths only"], "each group is intentionally mirrored (documented) or consolidated", True)
    if dup["tableNameCollisions"]:
        add("Review table-name collisions across services (%d)" % len(dup["tableNameCollisions"]), "duplication-candidate", "medium", [{"collisions": dict(list(dup["tableNameCollisions"].items())[:15])}], [],
            "integration lead", ["service db/**"], "overlaps are confirmed intentional per-service tables or consolidated", False)
    if screens["duplicateStaticEntrypoints"]:
        add("Duplicate static page entrypoints for screens", "duplicate-screen", "high", [{"screens": screens["duplicateStaticEntrypoints"]}], [], "UI owner", ["apps/web/app/**"], "one entrypoint per screen", True)
    sev = {"high": 0, "medium": 1, "low": 2}
    items.sort(key=lambda i: (sev[i["severity"]], not i["confirmed"], i["id"]))
    for r, it in enumerate(items, 1):
        it["rank"] = r
    return {"schemaVersion": "synqoria.reconciliation.backlog.v1", "ranking": "severity (high>medium>low), then confirmed before unconfirmed, then id", "itemCount": len(items),
            "confirmedCount": sum(1 for i in items if i["confirmed"]), "items": items,
            "note": "confirmed=false items are audit work, not proven gaps. No item instructs rebuilding a module from a naming signal."}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--baseline", required=True)
    ap.add_argument("--repo", default=".")
    ap.add_argument("--out", default="registers/reconciliation")
    a = ap.parse_args()
    root = os.path.abspath(a.repo)
    R = Repo(root, a.baseline)
    inputs = load_inputs(root)
    out = os.path.join(root, a.out)
    manifest = pkg01_manifest(R, inputs)
    canon = pkg02_canonical(R)
    hist = pkg03_historical(R, manifest)
    prov = pkg04_provenance(R)
    dup = pkg05_duplicates(R, prov)
    mods = module_evidence(R, inputs, canon, prov)
    screens = pkg26_screens(R, canon, mods)
    contracts = pkg27_contracts_security(R, prov)
    inventory = pkg28_inventory(mods, canon)
    backlog = pkg30_backlog(mods, screens, contracts, dup, canon)
    for name, obj in (("manifest.json", manifest), ("canonical-inventory.json", canon), ("historical-inventory.json", hist), ("provenance.json", prov), ("duplicate-fingerprints.json", dup),
                      ("module-evidence.json", {"schemaVersion": "synqoria.reconciliation.modules.v1", "baselineSha": R.sha, "modules": mods}), ("screen-coverage.json", screens),
                      ("contracts-events-security.json", contracts), ("module-inventory.json", inventory), ("backlog.json", backlog)):
        jdump(os.path.join(out, name), obj)
    print("baseline", R.sha, "modules", len(mods), "screens", screens["screenCount"], "backlog", backlog["itemCount"])


if __name__ == "__main__":
    sys.exit(main())
