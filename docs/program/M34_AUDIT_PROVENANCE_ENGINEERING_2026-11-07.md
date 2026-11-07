# SYNQORIA M34 — Audit, provenance and evidence-retention engineering

Branch: `work/chatgpt-m34-audit-provenance-2026-11-07`
Stack base: PR #36 / `work/seven-operations-real-workflows-2026-11-06`
Status: isolated SOURCE workstream. **No institutional, clinical, legal or certified retention acceptance**.

M34 owns no medical identity or original clinical notes. Upstream applications submit only metadata,
opaque record references, source revision digest and independently attested event envelopes.
No raw clinical documents or narrative are stored in the M34 evidence chain/outbox.

- `services/audit-core`: Java25 Spring Boot4.1.2 + JDBC with expected restricted role
  `synqoria_audit_runtime`, verified scoped transaction GUCs, OAuth2/OIDC issuer, audience and 15-minute staff grant expiry.
- `db/migration/V001__m34_audit_provenance_evidence.sql`, `V002__m34_attested_envelope_and_chain_guards.sql`:
  nine PostgreSQL FORCE RLS tables, immutable evidence and administrative action logs,
  immutable source attestations and legal holds, non-owner role and SQL-reviewed approval scope.
- Append-only per-stream cryptographic SHA-256 hash linkage; database enforces stream-head updates
  point to stored event evidence. External issuer proof binds all envelope fields and is non-writable by runtime.
- Attested source event INGEST, chain integrity verification, scoped searches, per-record event details,
  provenance timeline, independently approved export **manifest metadata only**,
  and independently reviewed retention action **with no data deletion**.
- No claim that a digest equals a qualified digital signature, legal evidence seal, completed archival
  transfer or signed clinician document.
- No real upstream P03 identity source, hospital audit producer, signed evidence issuer, M32 consent
  disclosure check, encrypted object/archive provider or retention schedule has been connected.
- Security, backup, load, accessibility and actual hospital acceptance remain DENIED.

## Workstations and isolated build
- Added five bilingual screen workstations: `/workforce/audit/M34-S01` through `M34-S05`, from canonical route `apps/web/app/workforce/audit/[screen]`.
- The audited browser BFF only supports searching/details, timeline, chain verify, export manifest request/review and retention request/review. Browser cannot access source ingest or independent proof/hold writes.
- `.github/workflows/m34-audit-web.yml` builds actual Next16/React19 source in an isolated CI-only PR23/PR24 dependency overlay; source does not modify global dependency owners.
- External clinical producer contracts, independent court/retention authority, signed archival service, real staff IdP and legal approval remain open.
