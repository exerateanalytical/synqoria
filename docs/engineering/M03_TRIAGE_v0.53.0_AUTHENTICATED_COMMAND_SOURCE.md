# SYNQORIA v0.53.0 — M03 Triage authenticated transactional source integration

## Scope and provenance

Verified source: `SYNQORIA_v0.51.0_60FEATURE_cumulative.zip`, SHA-256 `310a504a3f6233c3ce579e8c7a39ca5823c5c27b9af5b80a5114ec6b09e15de8`; all 1,460 source hashes passed before materialization. Preserved 40 modules, 261 screens, original specifications, and uncompleted clinical gates.

## Implemented, tested features

- **P06-B0169–B0217:** 49 discrete M03 workflow mutations routed through `TriageAuthenticatedCommandGateway` and `TriageCommandCoordinator` with server-verified identity, patient/facility/tenant scope, optimistic revision, per-command replay checks, hash-chain audit and atomic outbox/receipt via transactional persistence port. Each has a separate executable JVM integration test using a synthetic atomic store, including refusal of stale revisions, unauthorized scope, duplicate operations and superfluous parameters.
- **P06-B0218–B0227:** 10 distinct authorized read projections from case-specific, bounded, transactional aggregate reads; deny cross-scope case existence. Each verified in its own JVM using the synthetic store.
- **P06-B0228:** encrypted V017 quarantined schema (case bytes, audit, outbox and receipt tables), RLS ENABLE+FORCE, public privilege revocation, scoped keys and foreign-key constraints, SQL negative mutation tests. No grants or runtime policies by design; database is not deployment-ready.

Cross-cutting implementations: deterministic bounded `TriageSnapshotCodec`; AES-256-GCM `TriageEnvelopeCrypto` with scope in AAD and external key resolver; `TriagePersistencePort`; `TriageJdbcPersistence` adapter using row locks and scoped revision updates; atomic event/outbox and idempotency receipts; transaction failure injection tests. **The JDBC adapter is compiled only, not actually executed against PostgreSQL.** Encryption tests use synthetic test-only keys, not production KMS.

## Evidence, limits and risk

- 60/60 individual feature-level batch tests passed, see `evidence/v520/individual-60-execution.json`.
- 11/11 broader regression suites passed, see `evidence/v520/regression-commands.json`. Includes original 60 M03 domain tests, synthetic triage journey, Java transaction failure-injection tests, 12 SQL adversarial mutation tests, 16 immutable migration checksums, 109 UX-core tests, registers, native/web source constraints.
- SQL is quarantined and cannot run for operational roles because no app policies/grants exist. No live PostgreSQL RLS/locking/concurrency/rollback evidence; no authenticated HTTP end-to-end tests; no KMS, deployed app or native device tests; no human clinical/security sign-off. This work is **not production-ready, pilot-qualified or clinically accepted**.
- P06 remains NOT_CERTIFIED; 0/261 clinical screens accepted and 0/40 modules clinically certified.
- Next canonical implementation batch **P06-B0229**: secure authenticated HTTP request adapter with versioned contracts and runtime integration against reviewed restricted-role PostgreSQL, as prerequisites permit.
