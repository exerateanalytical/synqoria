# SYNQORIA v0.5.0 — Engineering review and evidence boundaries

Date: 2026-10-29 UTC. All sample identifiers, event types and test inputs are synthetic. No clinical data was accessed.

## Scope of ten newly verified implementation batches

| Batch | Actual deliverable | Verification boundary |
| --- | --- | --- |
| P01-B0004 | `V002` transactional outbox/inbox DDL, composite tenant/facility keys, event uniqueness, forced default-deny RLS | 12 static assertions; PostgreSQL NOT executed |
| P01-B0005 | `V003` master patient index, keyed identifier token and consent DDL, composite scope, forced default-deny RLS | 13 static assertions; PostgreSQL NOT executed |
| P01-B0006 | In-memory concurrent idempotency coordinator, request fingerprint conflict and replay receipt | 13 Java assertions; NOT durable |
| P01-B0007 | In-memory fenced dispatcher lease, event metadata JSON Schema | 11 Java assertions + 8 schema cases; no broker or DB |
| P02-B0005 | Exact navigation root vs child classifier; no sensitive query strings or sidebar detail entries | 3 TypeScript/Node tests; not Next route integration |
| P02-B0006 | Async patient switch with cache clearing before authorization, cancellation, stale-result guards | 3 TypeScript/Node tests; no real patient API/device |
| P03-B0004 | Identity-verifier admission seam, issuer/audience/MFA/time/revocation/grant checks | 14 Java assertions using a **stub** verifier; no OIDC cryptography |
| P03-B0005 | Explicit consent predicate scoped by tenant/facility/patient/purpose/operation/revocation freshness | 11 Java assertions; no approved clinical/legal basis |
| P04-B0002 | Revision-CAS facility lifecycle transitions | 11 Java assertions; in-memory only |
| P04-B0003 | Trusted cell routing directory with pinned HTTPS endpoints and active tenant/facility checks | 15 Java assertions; no TLS/DNS/network |

**Verification definition:** The ten batches have working source and *only the listed unit/static checks*. They do not constitute ten completed clinical features, ten certified batches, or a phase gate. No new canonical clinical screen is implemented. This is a cumulative source checkpoint.

## Test evidence

- `evidence/batches/P01-P04-v050-ten-batches.json`: ten individually invoked checks, full commands, exit codes, stdout and stderr.
- `evidence/batches/P01-P04-v050-checks.json`: 17 available regression commands, including strict TypeScript compilation, Java 21 compilation, 24 Node tests, 138 Java assertions, 29 legacy standalone static Chromium checks, draft OpenAPI validation, SQL source assertions and JSON Schema checks.
- `evidence/batches/P01-B0002-npm-ping-v050.json`: failed npm ping, `EAI_AGAIN`. No Next.js or Expo build or lockfile verified.
- Initial route test failure was caused by an incorrectly chosen *valid* version-1 UUID in a negative test fixture. The fixture was changed to an invalid version-0 UUID; the final full suite passed. No production issue is claimed to have been fixed by that fixture change.

## Security and reliability review

1. **Database:** V001–V003 define intended composite scope and RLS default deny, but there is no database execution, application DB role, migration rollback, tenant IDOR test or secure identity-to-DB session mapping. Never grant access from request-controlled GUCs or headers.
2. **Idempotency and outbox:** the in-memory implementations are reference algorithms only. They cannot prevent duplicates after process crashes, across nodes or across independent DB transactions. Implement durable unique constraints, atomic command/outbox writes, fencing in DB, reconciliation and failure injection before clinical writes.
3. **Identity:** the admission boundary expects a separately cryptographically verified identity. It does **not** validate JWTs. Real issuer discovery, pinned JWKS trust, signature algorithms, key rotation, session revocation and MFA proofs remain blocked.
4. **Consent:** an explicit predicate cannot substitute for legally reviewed consent, emergency exceptions, proxy authority, data sharing policies or consent-revocation propagation.
5. **Navigation:** client-side filtering and patient-switch state are defense-in-depth UX only. All routes and data access must be denied at authoritative server boundaries; no existence disclosure.
6. **Hospital routing:** the cell route directory only chooses a trusted configured HTTPS URL. It does not establish network identity, TLS certificate validation, mTLS, DNS rebinding resistance or disaster recovery.
7. **Clinical safety:** no triage prioritization, medication decision support, prescribing, allergy verification, order signing or bedside safety rules have been fabricated. Human clinical and regulatory acceptance remains mandatory.
8. **Visual/mobile:** no compiled Next.js or Expo apps, device builds, screen-reader audits or local Inter font verification. Prior Chromium checks exercise only static specimens.

## Canonical scope and gate state

Exactly 40 canonical modules and 261 canonical screens remain specified but not designed/implemented/tested/independently reviewed/clinically reviewed/accepted as deliverable screens. See `registers/screen-status-matrix.json` and `registers/module-status-matrix.json`. The source-level utilities above do not change clinical screen counts. P01–P04 are **in progress** and **not certified**. P05–P19 remain planned.

## Next dependency-unblocked batch

`P03-B0006`: protected append-only audit evidence hash-chain **domain reference** with adversarial tests; cannot be certified without durable storage, key management and independent security review. `P04-B0004` may follow for staff-grant revocation. `P01-B0002` remains blocked by npm DNS, and PostgreSQL/OIDC/mobile/pilot release gates remain blocked. A production release is explicitly denied.
