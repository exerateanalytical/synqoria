from pathlib import Path
import json,hashlib,datetime
r=Path(__file__).resolve().parents[1]
now=datetime.datetime.now(datetime.timezone.utc).isoformat()
old=json.loads((r/'evidence/CUMULATIVE_FILE_SHA256.json').read_text())['files']
ignore={'evidence/CUMULATIVE_FILE_SHA256.json','evidence/batches/v160-changed-file-manifest.json'}
new={}
for p in r.rglob('*'):
 if not p.is_file() or any(x in {'node_modules','dist','.next','.expo','__pycache__','.git'} for x in p.parts) or p.suffix in {'.pyc','.class'}:continue
 name=p.relative_to(r).as_posix()
 if name in ignore:continue
 new[name]=hashlib.sha256(p.read_bytes()).hexdigest()
changed=[{'path':k,'before_sha256':old.get(k),'after_sha256':new.get(k),'change':'modified' if k in old and k in new else ('added' if k in new else 'removed')} for k in sorted(set(old)|set(new)) if old.get(k)!=new.get(k)]
(r/'evidence/batches/v160-changed-file-manifest.json').write_text(json.dumps({'base':'v0.15.0','checkpoint':'v0.16.0','generated_utc':now,'changed':changed},indent=2)+'\n')
(r/'evidence/batches/v160-regression-timeout.json').write_text(json.dumps({'attempt':'SYNQORIA_EVIDENCE_TAG=v160 python tools/run_checks.py','result':'interrupted_by_execution_timeout','claim':'NOT_PASSED','recovery':'Separately executed Java, TypeScript, browser, source and 23 Python checks; see their individual logs and evidence.'},indent=2)+'\n')
status='''# SYNQORIA — Engineering checkpoint v0.16.0

**Continuity:** From persistent `/SYNQORIA/SYNQORIA_CURRENT.zip` v0.15.0, matching published SHA-256 `d2766b80c85bbe92b9eab6284edb0a3e61d6a14932b87c0ba22f8d1ed5a8f97f`. All 370 prior manifest-listed source files matched SHA-256; initial seed manifest 19/19 matched.

## Actual implemented batches: 10/10, unit/source tested only

- **P05-B0022–B0026 (5):** package-private provisional JDBC write seam with public always-locked admission; append-only review event state machine; domain-separated event hash chain; reviewer grant epoch freshness; review-only IAM/revocation/database readiness composition. **35 new Java assertions**, individually invoked; see `evidence/batches/v160-P05-*.log`.
- **P02-B0040–B0043 (4):** canonical intake route deep-link guard; EN/FR accessible non-identifying status summary; volatile native draft lifecycle invalidation; staff React Native preview wired to accessible review status, remaining locked. Three individually executed TypeScript tests plus one source integration test. No native build.
- **P01-B0032 (1):** executable release-denial gate with canonical module/screen and blocker assertions, wired into proposed CI workflow; release request returns nonzero exit code 3 in a real subprocess test. CI was not executed remotely.
- Individual batch execution receipts: `evidence/batches/P01-P05-v160-ten-batches.json` and ten `v160-P*.log` files. All recorded exit 0; no production/clinical integration is claimed.

## Independent regression evidence

- `npm run check`: **76/76** TypeScript/Node tests, canonical 40 modules/261 screens and navigation tests passed (`v160-ux-regression.log`).
- `bash tools/check_java_security.sh`: complete inherited Java suite plus 5 new batches passed (`v160-java-regression.log`). The new 5 batches total **35 assertions**.
- `npm run check:source`: **4 TSX syntax parses** plus web/mobile static source checks passed (`v160-source-regression.log`); no dependency resolution or native compilation.
- **48/48** standalone Chromium specimen checks passed (10 legacy, 19 EN/FR, 19 registration) at 9 widths and simulated 200% font (`v160-*-browser.log`). These are NOT compiled Next.js or native app tests.
- **23/23** additional Python contract, ledger, source and SQL-static tests passed individually (`v160-static-regression.json`). SQL was NOT executed in PostgreSQL.
- An attempted monolithic `tools/run_checks.py` command **timed out** and is **NOT counted as passed** (`v160-regression-timeout.json`); separately completed checks are recorded above.
- `tests/test_v150_checkpoint.py` was made forward-compatible without removing v0.15.0 historical evidence assertions.

## Unresolved gates (no clinical release)

- **0/261** canonical screens implemented/clinically reviewed/accepted; **40/40** module IDs and **261/261** screen IDs preserved.
- No real PostgreSQL, restricted roles/RLS isolation tests, durable reviewer decision/event chain, real OIDC/JWKS, independent clinical approval, HSM/key custody, audit immutability or patient-scope integration. Hash chain is in-memory logic only.
- No verified Next.js dependency build, Expo Android/iOS binaries, device screen reader, physical device/offline tests or full production visual acceptance. Static previews do not meet UX01–UX10 release gates.
- No verified SYNQORIA GitHub repository or push. The persistent Library archive remains source authority.
- **P01/P02/P03/P04/P05/P19 are not certified.** The public registration production boundary remains locked even with caller-supplied true flags; `RegistrationJdbc` is package-private and retained solely as a legacy mock-JDBC test seam.

## Exact next batch

`P05-B0027`: implement and test restricted-role PostgreSQL atomic reviewer decision, revocation event and outbox/audit transaction with real tenant-isolation evidence once a database environment is available. Independent next work: `P03-B0017` verified OIDC/JWKS claim binding and `P02-B0044` genuine compiled web/mobile intake after dependency provisioning. Continue unrelated dependency-free implementation if blocked.

**Release status:** `NOT_CERTIFIED`. All safety and human clinical acceptance gates remain fail-closed.
'''
(r/'docs/program/STATUS.md').write_text(status)
(r/'docs/program/V016_REVIEW_EVENT_AND_LOCKED_INTAKE.md').write_text('''# v0.16.0 — Review authority and native intake engineering review

This checkpoint narrows an exposed API surface: `RegistrationJdbc` is now package-private, and `RegistrationProductionBoundary` exposes only locked results. The existing mock-JDBC test is not a production registration endpoint. A real backend handler MUST NOT call it until durable independent reviewer authorization, tenant RLS, audit and clinical approval exist.

`ReviewEventTransition` accepts NONE→APPROVED→REVOKED only with consecutive versions; it cannot resurrect a revoked decision. `ReviewAuditChain` binds the prior hash, event kind, event version and a SHA-256 binding. It does not persist records, authenticate an event author or substitute for protected audit custody. `ReviewerGrantEpoch` compares a previously trusted session against a current authoritative grant epoch; neither model is an OIDC verifier. `ReviewReadinessReport` always returns an evidence/lock status, never a patient-write permission.

The TypeScript route guard accepts only the exact workforce registration path and capability and rejects query, fragment, encoding and deep-link variations. It is defense in depth: the backend must deny unauthorized existence. Accessible EN/FR status summaries expose no patient identity; the native staff preview remains read-only and never mounts editable demographics. Native lifecycle logic clears volatile drafts on suspension/offline transitions. Screen M01-S02 remains **not implemented** in the canonical register because there is no authenticated, authorized clinical workflow.

`verify_release_gate.py` has a deterministic nonzero release-denial exit code and is wired into the source CI workflow; CI itself was not run. External approval, signed release manifests, OIDC, PostgreSQL, clinical content and device tests are not available. See batch logs, changed manifest, blockers and status. No hospital pilot certification.
''')
(r/'README.md').write_text('''# SYNQORIA — Africa medical intelligence synchronized

**Cumulative engineering checkpoint v0.16.0; NOT clinically certified, NOT production ready.**

- Authoritative architecture and UI baseline: `docs/authority/`; canonical 40-module / 261-screen registers: `registers/`.
- Source: `apps/web`, `apps/staff-mobile`, `apps/patient-mobile`, `packages/ux-core`, `services/clinical-core`.
- This checkpoint: five Java reviewer/registration safety batches, four TypeScript/native intake safety batches, one executable release-denial gate.
- Evidence: `evidence/batches/P01-P05-v160-ten-batches.json`, ten individual logs, regression logs and `v160-changed-file-manifest.json`.
- All 261 canonical clinical screens remain unimplemented; prototypes remain locked. No real PostgreSQL/OIDC/Next.js/Expo integration.
- Current blockers, test scope and exact next batch: `docs/program/STATUS.md`, `registers/blockers.json`.
''')
# remove this generator's temporary files? kept as engineering provenance.
print('Changed paths',len(changed),'new',sum(x['change']=='added' for x in changed),'modified',sum(x['change']=='modified' for x in changed))
