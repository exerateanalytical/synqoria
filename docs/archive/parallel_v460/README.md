# SYNQORIA — Africa medical intelligence synchronized

**Latest cumulative engineering checkpoint v0.43.0. NOT clinically certified; NOT production ready.**

Authoritative architecture/UI/UX documents are in `docs/authority/`. The exact inventory remains **40 modules and 261 stable screens**, with **0/261 clinically implemented or accepted**. The clinical registration writer and clinical release remain disabled.

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

## v0.46.0 P06-B0071
Immutable migration checksum lock and fail-closed mutation tests. Source-only; live database enforcement unverified.
