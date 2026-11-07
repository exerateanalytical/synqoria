# SYNQORIA M32 — Engineering handoff (source candidate)

Owner: ChatGPT M32. Branch `work/chatgpt-m32-consent-privacy-2026-11-07`.
Stack base: `work/seven-operations-real-workflows-2026-11-06` / PR #36.
No merge into main; clinical, institutional and jurisdiction-specific legal sign-off DENIED.

## Code-owned scope
- `services/privacy-core`: Java 25, Spring Boot 4.1.2, restricted PostgreSQL runtime and strictly scoped JWT.
- `services/privacy-core/db/migration/V001__m32_consent_privacy_governance.sql`: seven FORCE RLS tables.
- `contracts/openapi/synqoria-m32-privacy-v1.yaml`: twelve API operations.
- Independent evidence inserted only by a trusted upstream authority, **never by M32 runtime**.
- Proposal -> independent verify -> separate activate -> irreversible revoke. No unknown-to-consent inference.
- Scope-specific time-of-use Boolean consent evaluation and disclosure review. M32 approval **does not transfer any medical data**; export is the responsibility of M34/authorized clinical source.
- Privacy case open/independent close with immutable subject and categorization; intake-to-case reporting remains institution-controlled.
- Idempotent commands, transactionally linked metadata-minimized audit/outbox. No PHI in outbox.
- Database triggers enforce transitions and immutable evidence, SQL role separation and FORCE RLS.

## Hard acceptance gates
Current unit + synthetic PG tests are engineering checks, **not** proof of compliance, real legal authority or deployment. Real consent signer identity/proxy authority, clinical policy and time-of-use downstream enforcement, signed objects, certificate/retention jurisdiction profiles, staff OIDC grant issuance, external source revocation, clinical risk review, penetration and accessibility testing remain open.
Other workstreams: Claude M26/M24/M28/M30/M38/M39/M40; ChatGPT M27 and planned M34; identity P03 and clinical P06/P07 own their domains.

## V002 — independent evidence read-only and revocation serializing fence
- PostgreSQL row locking on evidence would require stronger runtime table privileges; the initial PG18 integration caught this mismatch.
- Runtime retains SELECT-only access; a transaction-scoped proof-ID advisory fence coordinates current proof validation with independent issuer revocation, and neither side may rewrite proof scope or undo revocation.
- PostgreSQL18.6 tests must pass at the exact pushed SHA before checkpoint is counted. No real issuer is connected yet.
