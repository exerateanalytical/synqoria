# SYNQORIA M16 — Source engineering handoff (2026-11-07)

Owner: ChatGPT, branch `work/chatgpt-m16-referrals-2026-11-07`, [draft PR #44](https://github.com/exerateanalytical/synqoria/pull/44).

**Commit at API contract addition:** stacked on PR #36; no merge to main.
**Implementation:** services/referrals-core (Java25 LTS, Spring Boot 4.1.2, JDBC, OIDC JWT issuer/audience),
PostgreSQL 18.6 (V001–V002; 6 FORCE RLS relations), browser CSRF/origin
allowlist, five bilingual responsive screen workstations at `/workforce/referrals/M16-S01..M16-S05`.
**API contract:** `contracts/openapi/synqoria-m16-referrals-v1.yaml`.

## Business/technical authority notes
- **Business state:** DRAFT → AUTHORIZED → SENT → ACCEPTED → TRANSFERRED → ATTENDED → CLOSED, with reject/cancel branches.
- **Authority:** external patient consent, receiving decision, handover and follow-up proof checked against exact patient/referral/receiving facility, with independent actor separation and revocation fencing.
- **Protection:** 6 FORCE RLS tables, immutable transition history, deferred proof-of-audit requirement, idempotency and metadata-only outbox.
- **Remaining:** actual hospital recipient grant issuer, independently verified consent and signed handover evidence, transport/clinical document authority, cross-facility acceptance integration, legal policy and clinical production sign-off.

## Engineering evidence
- Dedicated workflow verifies Java25/27 plus PostgreSQL18.6 with synthetic authorities.
- Dedicated web workflow verifies all canonical routes, Next16.4.5/React19.3.1 in an isolated CI-only PR23/24 dependency overlay. The root lockfile is never rewritten by this branch.
- All actual clinical/legal, identity and medical evidence integrations and hospital acceptance **DENIED until institutionally supplied and verified**.
- No claim of production medical or API interoperability certification. Root engineering CI still fails separately due to PR23/24-owned dependency integration.
- Separate Claude M24/M28/M30/M38/M39/M40 assignments and ChatGPT M27/M32/M34 PRs are not modified.

## Delivery boundary
- The receiving facility proof is attested externally and bound to referral+patient+destination. An actual cross-facility token/permission flow must be integrated before deployments; a sender cannot assert receiving acceptance merely by invoking the UI.
