# SYNQORIA — canonical continuous reconciliation and execution control
**Adopted:** 2026-11-07. **Authority:** current `main` source, current open PRs and independently verified checks. Older checkpoint markdown is historical, not the current completion ledger.

## Rule 0: no blind development, ever
At the start of **each** independent ChatGPT, Claude, Copilot or CI batch:
1. Fetch latest `main`, its HEAD, and all open PRs/heads; read `registers/current-platform-reconciliation.json` and `registers/engineering-ownership.json`.
2. Inspect the actual existing files, APIs, database migrations, forms, screen routes, prints and contracts for the proposed module. Historical unmerged branches and zipped checkpoints can contain valuable code and must be compared, not blindly replaced.
3. Confirm exclusive ownership of paths and business capability. If another developer has the work, review or integrate instead of recreating it. Create the smallest needed implementation slice.
4. Capture baseline SHA and exact changed paths in the PR; run module contract, DB/RLS, frontend and shared build checks that apply. **A status document or generated screen alone is not implementation evidence.**
5. Merge engineering source only after appropriate checks and scope review. Never bypass a failed test by removing a required gate. No clinical certification is asserted merely because source merged.
6. **Immediately after EVERY merge to main**, the `platform-reconciliation.yml` workflow retrieves live open PRs, scans tracked source and regenerates `registers/current-platform-reconciliation.json`. It commits its fresh report back to main with `[skip ci]` and stores an artifact. A failed reconciliation workflow is a blocking engineering issue to investigate, not an excuse to quote an old percentage.
7. On subsequent work, inspect the refreshed reconciliation and update the ownership file / prioritized backlog before assigning another module. When a PR is still open, do not count it as merged.
8. If a reviewer cannot confirm a module's code ownership or acceptance, mark it **unknown**, not **unbuilt** or **production-ready**.

## Non-overlapping ownership at this checkpoint
- **Claude:** M38 PWA device/SDK and sync client (PR #56 / issue #53), source owner only for identified SDK/offline client paths; current reported evidence 17/30 verified, 12 partial, 1 design-only. `main` already contains the M38 server (PR #50). Do not duplicate M38.
- **Integration lead:** canonical GitHub main reconciliation; P03 verified session; shared Next/React dependencies; M39 control-plane integration / gateway / provisioner; M40 backup agents, actual recovery and telemetry. M39 server PR #51 plus routing and health PRs #54/#58 and M40 server PR #52 plus backup inspection PRs #55/#57 are merged; these are **not** production hospital release.
- **Module review backlog:** M01–M37 inventory and reconciliation against existing stacked source and operational screen plans. **No one may declare those 37 modules absent based on a filename scan.**

## Next path forward, in strict dependency order
| Priority | Work package | Owner | Engineering output / acceptance |
|---|---|---|---|
| P0 | Fix M27 `@synqoria/ux-core` dependency resolution and duplicate shared React type failures | Integration lead | reproducible root/Next web build; failed lane reruns green |
| P0 | Establish verified P03 OIDC/JWKS identity and strictly scoped `/api/offline/session` integration | Integration lead + Claude interface review | signature, issuer, audience, expiry, user and cell/facility scope; revoked token denied |
| P1 | Rebase and review M38 PR #56 against current main | Claude + integration lead | no duplicate work; browser SDK/real cookie+proxy end-to-end tests; honest 30-package status |
| P1 | M39 real routing gateway admission, trusted service discovery and separate infrastructure executor | Integration lead | test hospital-cell isolation, maker/checker, health freshness, fail-closed routing on disposable 2-cell rig |
| P1 | M40 independent backup agent, verified object evidence and disposable WAL/PITR rehearsal | Integration lead | valid backup integrity plus actual restore/rollback evidence, measured RPO/RTO only for tested rig |
| P1 | Reconcile M01–M37 code, screens, forms, printables and reports against all merged and open source | Integration lead | evidence matrix 40/40: source files, service, API, database, UI, workflows, tests, precise gaps and owner |
| P2 | Implement truly missing cross-module workflows from reconciled backlog | Allocated per reviewed capability | actual tested production-stack code, no duplicated forms or screens |
| P3 | Release/security/clinical pilot gates and certification | Separate authorized reviewers | controlled real facility pilots and documented approval; no guessed readiness percentage |

## Counting policy (non-negotiable)
- **40 modules / 261 canonical screens** are design inventory, not a completion denominator for source-file heuristics.
- A `sourceEvidence: located` signal only means matching path names exist. `not_located_by_path_heuristic` does not mean unbuilt.
- Actual `implemented` requires individually reviewed code, runnable build, tested APIs and database, real workflow integration and ownership attribution.
- `production-integrated` requires live-environment-valid integration proof, authorization, migration and operation evidence.
- `clinically-accepted` requires authorized clinical reviewer approval, recorded independently.
- No percentages for fully built/remaining modules until all 40 evidence records are reconciled; unknown values stay `null`, never `0`.
- Screen and module status must never regress from real evidence; preserve source references even if implementation is incomplete.

## Process integrity
The bot attempts to commit its updated report after a main push. If branch protection denies bot pushes or another merge races it, the workflow fails rather than force-pushing. Its uploaded artifact remains available and an authorized maintainer must merge the refreshed register or grant the workflow narrowly scoped write access. Monitoring CI failures and acting on them is essential; a scheduled autonomous assistant is not implied by this workflow.

After each merge, report: actual main SHA, merge PR, new/changed capabilities, tests and failures, evidence of 40-module review, active PRs, next exclusive owner and next dependency. No unrelated module reimplementation without source comparison.
