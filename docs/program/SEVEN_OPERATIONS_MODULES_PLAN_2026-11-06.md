# SYNQORIA — seven administrative module implementation plan (2026-11-06)

**Owned modules:** M21 Procurement, M22 non-medication stores, M23 biomedical assets, M25 transport and ambulances, M29 hospital configuration, M31 document governance, M37 notifications. Canonical register: `registers/canonical-modules.json`; screen IDs from `registers/canonical-screens.json`. **35 canonical screens** (6 + 5 + 5 + 5 + 4 + 6 + 4).

This is an independently owned workstream from `main`. Avoid reusing P03/P06/P07 batch numbers and avoid clobbering P03 auth routes and PR #15/#16 document artifacts or PR #14/#18 screen routes. Use the new `services/operations-core/` domain and conditional UI integrations.

## Delivery plan

| Module | First workflow slice | Required remaining production integrations |
| --- | --- | --- |
| M21 | supplier-qualified requisition → approved → order → goods receipt → invoice matched → close | supplier qualification source, finance, tender/legal rules, PO printable |
| M22 | quarantine → release → reserve → issue → return, separately audited stock ledger | accurate lot/bin/location quantities, count approvals, cost, pharmacy exclusion |
| M23 | registered → commissioned → active → maintenance → safe return/retirement | biomedical inspection/maintenance sign-off, equipment service records |
| M25 | requested → assigned → dispatched → arrived → handed over → completed | driver/vehicle rosters, live location/ETA, emergency clinical handover |
| M29 | draft → reviewed → published → superseded | version impact, hierarchical tree, signed rollout to disconnected cells |
| M31 | templates draft → approved → published; document draft → signed → amended | approved forms library PR #16, signature verification and immutable external object storage |
| M37 | queued → delivered → acknowledged, retry/failure/escalation | message delivery adapters, healthcare notification redaction and proof of delivery |

## Non-negotiable engineering gates
- **Production target:** Java 21 + Spring Boot 4.1.x, PostgreSQL 16+, hospital-cell isolation, per-facility RLS, API-first with verified OIDC bearer scope and capabilities. Never derive staff authorization from a caller-provided role/header.
- Transactions: optimistic versioning, atomic idempotency, immutable audit and outbox; approved workflow actions only; maker-checker separation; no unsafe medical device clearance, signature, or emergency transport handover without verified evidence.
- No general SQL, untrusted schema identifiers, raw patient records, unverified medical alerts, or direct email/SMS dispatch from public requests.
- Every screen must have scope-proven data, permission-aware actions, bilingual EN/FR, mobile-first layout, accessible controls, empty/error/loading/denied/offline states and source-mapped tests.
- **Definition of done for a full module:** all canonical forms/screen flows + DB + API + contracts + authorization + operational integrations + realistic acceptance/security/operational tests, not merely a skeleton or state machine.
- Run actual Java compile/tests, source safety checks and disposable PostgreSQL RLS integration through CI. Source tests are not clinical acceptance. No production deployment, 0/261 clinical screens accepted, formal certification deferred; clinical release **DENIED**.

## Execution ownership
1. Reserved: isolated operations service / migrations / tests / workflow contracts and explicit adapter integration points.
2. Implement seven distinct domain lifecycles, IDs and 35 screen spec bindings.
3. Compile Java 21, run domain and security tests, execute PG16 migrations with a restricted non-owner role.
4. Integrate trusted OIDC authorization, audited PostgreSQL writes, read models and 35 responsive Next.js screens via reviewed PR rather than conflicting with existing workspace owners.
5. Record verified delivered vs remaining work separately for each module. Only classify a module as implemented after its entire acceptance checklist is met.
