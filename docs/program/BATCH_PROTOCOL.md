# Hourly build and evidence protocol (strict)

1. Retrieve latest authenticated checkpoint from the persistent Library and, when supplied, GitHub. Confirm hashes; do not guess versions or count earlier speculative output as code. Compare with the two authority documents and screen/module register.
2. Reserve next unfinished batch in the nearest dependency-unblocked phase; use separate worktrees/branches for parallel agents. Each batch identifies exact screen IDs, flows, API routes, states, migrations, mobile routes and tests.
3. **Plan → design → build → self-review → independent test → certify**. A batch is not certified unless real evidence exists. Never use a simulated completion report to pass a gate.
4. Target ten (10) meaningful, individually verified source-level implementation batches per run; if fewer are possible, report the actual number and why. Move to the next phase when its gate passes; no one-phase-per-hour cap. If a safety/credential/partner/hardware blocker persists, file it with owner, affected IDs, workaround, and next step; continue independent safe tasks. Never bypass authentication, patient safety, regulatory approvals or fail-closed gates.
5. Run real compilation where available; unit/contract/integration/e2e, tenant/IDOR, wrong patient, stale writes, retry/double-effects, accessibility, EN/FR, screen-size visual audits. Do not report unexecuted tests as passing. All medical data in artifacts must be synthetic.
6. Maintain distinct statuses per deliverable: specified, designed, implemented, tested, independently reviewed, clinically reviewed, accepted. CI green alone is not clinical certification.
7. Export a complete **cumulative** artifact (not deltas only), SHA-256, changed-file manifest, latest phase/batch, screen coverage, tests actually run with results, risks, blockers, and next allowed work.
8. Save cumulative ZIP and checkpoint to durable Library `SYNQORIA` folder (and to repository only if an actual authorized repository exists). Never claim push or upload without response proving success.
9. Stop scheduling / request disabling the hourly job only after every phase and screen meets its applicable gates and actual authority-approved pilot/release acceptance is evidenced. Keep reporting unresolved blockers, including need for hospital clinical sign-off.

Batch nomenclature: `Pxx-Bxxxx` (e.g. `P02-B0001`). State is cumulative, monotonic only for verified outcomes. Ten per run is a target, not permission to fabricate, split trivial work, or bypass evidence gates.
