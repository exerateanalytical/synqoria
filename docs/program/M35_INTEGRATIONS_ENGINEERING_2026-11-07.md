# SYNQORIA M35 — Source engineering handoff (2026-11-07)

Owner: ChatGPT, branch `work/chatgpt-m35-integration-management-2026-11-07`, [draft PR #45](https://github.com/exerateanalytical/synqoria/pull/45).

**Commit at API contract addition:** stacked on PR #36; no merge to main.
**Implementation:** services/integration-management-core (Java25 LTS, Spring Boot 4.1.2, JDBC, OIDC JWT issuer/audience),
PostgreSQL 18.6 (V001–V004; 10 FORCE RLS relations), browser CSRF/origin
allowlist, six bilingual responsive screen workstations at `/workforce/integrations/M35-S01..M35-S06`.
**API contract:** `contracts/openapi/synqoria-m35-integrations-v1.yaml`.

## Business/technical authority notes
- **Control plane:** partner KYB and two-person enablement, API product version publication, developer app approval with credential status fixed to NOT_ISSUED, versioned mapping validation, source-attested quarantine and two-person replay review.
- **Safety:** 10 FORCE RLS PostgreSQL tables, immutable evidence and audit/outbox, scoped JWT permissions, independent reviewers, no browser access to source ingest, no PHI messages.
- **Replay:** APPROVED_PENDING_DELIVERY does not execute a network request. CredentialsIssued=false and dispatched=false are explicit API fields.
- **Remaining:** actual KYB issuer, product gateway, OAuth client registration/secrets/HSM, FHIR/HL7 transport adapters, broker/worker, quarantine replay sender, external audit certification, security and hospital pilot approval.

## Engineering evidence
- Dedicated workflow verifies Java25/27 plus PostgreSQL18.6 with synthetic authorities.
- Dedicated web workflow verifies all canonical routes, Next16.4.5/React19.3.1 in an isolated CI-only PR23/24 dependency overlay. The root lockfile is never rewritten by this branch.
- All actual clinical/legal, identity and medical evidence integrations and hospital acceptance **DENIED until institutionally supplied and verified**.
- No claim of production medical or API interoperability certification. Root engineering CI still fails separately due to PR23/24-owned dependency integration.
- Separate Claude M24/M28/M30/M38/M39/M40 assignments and ChatGPT M27/M32/M34 PRs are not modified.

## V004 attestation integrity
- Source-message proof now binds exact event type in addition to partner, external event ID and digest. PostgreSQL admission checks the independently attested event fields before INSERT, so direct SQL cannot forge a different event classification.
- A proposed replay's independent approval rechecks the current, non-revoked source attestation. No network replay is dispatched and no API credentials are generated.
