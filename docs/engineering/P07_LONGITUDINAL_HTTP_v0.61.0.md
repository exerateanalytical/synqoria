# SYNQORIA v0.61.0 — M04/M05/M06 authenticated HTTP vertical slice

**Status:** implementation + synthetic loopback integration tests; **not** production qualified, not certified.

## New code

- `LongitudinalAuthenticatedGateway.java` verifies server-provided identity, patient/facility/tenant scope, fresh authorization proof and per-operation grants before allowing domain commands; also provides privacy-bounded encounter summary, audit history, confidential note reads and filtered observations. Case reads require verified scope and intact audit chain.
- `LongitudinalStrictJson.java` is a purpose-built, size-bounded, duplicate-rejecting JSON decoder. It refuses actor/tenant/facility evidence from client bodies; command ID, revision, operation and `inputs` are the only allowed root fields. It is not a general JSON library.
- `LongitudinalHttpHandler.java` is an **opt-in** HTTP boundary using injected `BearerAdmission` and server-side `ClinicalEvidenceSource`; exact scoped paths only, no credential cookies or tenant headers, `Cache-Control: no-store`, error-suppressed clinical existence and explicit stale-revision responses. It does not install a real production servlet/router/OIDC/RLS adapter.
- `packages/ux-core/src/longitudinal-client.ts` provides a typed, in-memory-token staff transport with offline prohibition, cancellation/scope-switch fences, response-envelope privacy checks and strict read-model decoders. No live Next.js or Expo builds were executed.
- `contracts/openapi/synqoria-m040506-restricted-v1.json` is the versioned, authenticated command/read contract with no client-authoritative actor scope.
- `LongitudinalHttpBoundaryTest.java` uses a **synthetic** trusted session and in-test atomic store, running against a live loopback HTTP server. This is not real OIDC, a deployed API, PostgreSQL or clinical certification.

## Evidence, changed files and batches

Batch IDs: P07-B0061–B0070. 10/60 target (16.67%), 50/60 not completed (83.33%). Evidence: `evidence/v610/P07-B*.log`, `evidence/v610/verified-batches.json`; 8 individually executable Java HTTP scenario groups, 5 OpenAPI checks and 6 TS client checks, plus Java compilation and aggregate regression.

- Baseline P07 domain regression: all 60 previously implemented operations passed in a fresh run; the 35 clinical boundary adversarial assertions passed (not counted as new batches).
- `npm run check:ux`: 292/292 passed.
- `npm run check:registers`, `npm run check:source`, `python tools/verify_status_matrix.py`: each passed.
- Exactly 40 canonical modules and 261 stable screens remain unchanged; zero accepted clinical screens, zero clinically certified modules.

## Technical limitations and safeguards

**Do not deploy this HTTP boundary with the test-only UnitTestClinicalStore.** The production `ClinicalCaseCore.Persistence` port needs reviewed, scoped PostgreSQL transactions with RLS, row locks, atomic encounter/audit/outbox/idempotency writes, serializer integrity, measured concurrency tests, failover and patient-level retention governance. A cryptographically validating and revocation-aware OIDC/MFA verifier with fresh clinical access evidence is mandatory. These are not supplied by this implementation.

No clinical thresholds, prescribing rules or automated medical recommendations are introduced. Human clinical sign-off and formal certification remain deferred, not waived. No clinical data used in synthetic test fixtures.

## Parallel source and next steps

Based on `work/clinical-encounter-documentation-v060` / PR #3, not GitHub main. Never overwrite main or the shared Library CURRENT artifact without reconciling the newer main-only recovery and project-memory changes. Next P07 candidate: **P07-B0071** (real PostgreSQL transactional adapter and server route wiring, or separately implementable mobile clinical workflow). Parallel P06 triage candidate remains **P06-B0392**; do not reuse this batch ID.
