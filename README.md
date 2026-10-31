> **Current internally verified source checkpoint: v0.57.0**. Certification work is deferred at user direction; security/clinical release gates stay closed.

# SYNQORIA — Africa medical intelligence synchronized

**Current cumulative engineering checkpoint v0.56.0. NOT clinically certified; NOT production ready.**

Authoritative architecture/UI/UX documents are in `docs/authority/`. The exact inventory remains **40 modules and 261 stable screens**, with **0/261 clinically accepted screens** (engineering source work is distinct from acceptance). The clinical registration writer and clinical release remain disabled.

v0.25.0 adds a static PostgreSQL migration safety audit, restricted-role readiness probe, build prerequisite diagnostics, a source-level accessible nonclinical mobile More panel, and a package-private mock-JDBC reviewer event/outbox/audit transaction adapter with an **unapplied** RLS-protected schema draft. None of these is a live PostgreSQL, compiled Next/Expo, production OIDC or clinical workflow integration.

Read `docs/program/STATUS.md`, `docs/program/V025_POSTGRES_ADAPTER_AND_ACCESSIBLE_MORE_REVIEW.md`, `registers/blockers.json` and `evidence/batches/P01-P02-P05-v250-ten-batches.json` for actual evidence and limits.

## v0.28.0 checkpoint

Ten individually verified engineering batches (P01-B0043–B0044, P02-B0093–B0094, P06-B0001–B0006) add build attestation, manual dependency-lock proposal, mobile More accessibility and M02 scheduling domain foundations. All clinical writes remain disabled; **not production-ready**. See `docs/program/STATUS.md`.


v0.33.0: Three tested scheduling safety foundations; see docs/program/STATUS_v0.33.0.md.


v0.40.0: P06-B0043 transition + scoped optimistic update binding (mock JDBC).

## v0.41.0 checkpoint

Twenty independently verified P06-B0044–B0063 scheduling foundations (Java unit/mock-JDBC plus two static schema checks) cover quarantined transaction orchestration, scoped locks, idempotency, audit/outbox, role/RLS proof, bounded retry/timeouts and lifecycle parity. **No live PostgreSQL, application HTTP write route, clinical screen acceptance or production release.** See `docs/program/STATUS_v0.41.0.md` and `evidence/batches/v410-executed.json`.

## v0.42.0 reconciled checkpoint

The Library v0.41.0 historical P06-B0044 implementation was merged and hardened; twenty additional individually verified batches P06-B0045–B0064 provide quarantined scheduling transaction, idempotency, audit/outbox, scope, lifecycle and replay safeguards. **Not live PostgreSQL tested, not clinical or production certified.** See `docs/program/STATUS_v0.42.0.md`.

## v0.43.0 parallel-branch reconciliation

The separately uploaded v0.42.0 (three mocked-JDBC batches) was reviewed. Its original source/evidence are preserved under `docs/archive/v042_parallel/` and `evidence/batches/v420-concurrent-historical.json`. The active adapter remains the stronger schema-aligned, atomic and mock-tested source; 20 P06-B0045–B0064 batch IDs are not double-counted. See `docs/program/STATUS_v0.43.0.md`.


## v0.45.0 scheduling integrity checkpoint
See `docs/program/V045_ENGINEERING.md` and `evidence/batches/v450-executed.json`. Live PostgreSQL and clinical release remain blocked.


## v0.47.0 reconciled checkpoint
Fifteen new verified scheduling batches plus two independently tested migration gates from a parallel source; see `docs/program/V047_RECONCILIATION.md`. Clinical release denied.


## v0.48.0 reconciled checkpoint
15 newly verified batches and 4 independently tested parallel migration-gate components. See `docs/program/V048_RECONCILIATION.md`; clinical release denied.


## v0.52.0 — M03 triage source-level implementation

60 independently run clinical-workflow feature tests P06-B0094..B0153 cover intake, queues, assessment evidence, human-entered priorities, handover and authorized projections. See `docs/engineering/M03_TRIAGE_v0.52.0_INTERNAL.md` and `evidence/v510/`. V015 SQL remains quarantined. No live database, Next/Expo build, clinical acceptance or production approval is claimed.

## v0.52.0 M03 persistence checkpoint

Internal-only restricted triage JDBC adapter and quarantined V016 encrypted snapshot migration. See `docs/engineering/M03_TRIAGE_v0.52.0_RESTRICTED_PERSISTENCE_INTERNAL.md` and `evidence/v520/verified-batches.json`. No live PostgreSQL/RLS, KMS, OIDC, web/native build or clinical release attestation. Next P06-B0169.


## v0.53.0 reconciled M03 command/read checkpoint
The concurrent v0.52.0 restricted JDBC branch was preserved intact (P06-B0154–B0168), and 60 separate authenticated command/read/security-schema batches were added as P06-B0169–B0228. Evidence is in `evidence/v530/` and `registers/v530-reconciled-60-batches.json`. V016 and V017 SQL remain quarantined with no application grants or approved RLS policies. No live DB, OIDC, HTTP, Expo device or human clinical review. Release DENIED.

## v0.55.0 triage security checkpoint

Revision-one audit anchor, encrypted snapshot v2, persisted event/receipt/outbox verification, quarantined V019 correlations and scoped history GET transport were internally tested. See `docs/engineering/M03_TRIAGE_AUDIT_CONTINUITY_v0.55.0.md`. No production deployment or clinical certification.

## v0.56.0 — Typed M03 clinical HTTP and worklist integration

49 existing M03 commands newly mapped for typed staff clients and independently verified across loopback HTTP; eight new patient-scoped worklist HTTP views, intake HTTP creation and typed observation record/correction support. Details `docs/engineering/M03_TRIAGE_HTTP_v0.56.0_INTERNAL.md`. **NOT live PostgreSQL/OIDC, not approved clinical workflows. Clinical release DENIED.**

## v0.57.0 — Patient-scoped triage read/workspace integration
Six focused implementation batches P06-B0363–B0368 provide strict summary and paged audit GET transport, volatile case context, scoped worklists, bounded audit pagination, and OpenAPI parity. A staff React component was authored and syntax-reviewed but is not counted as application-build verified. See docs/engineering/M03_TRIAGE_v0.57.0_INTERNAL.md and evidence/v570/.
