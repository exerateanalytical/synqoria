# SYNQORIA — M21/M22/M25/M29/M37 durable connector batch (2026-11-07)

**Ownership:** ChatGPT integration lead, PR in branch `work/chatgpt-five-module-durable-connectors-2026-11-07`. Reuses all prior M21/M22/M25/M29/M37 production source. Claude's completed M38 PR #56 and forensic inventory PR #65 are merged. **No hospital/certification acceptance.**

## One coordinated deployment package

New source is `OperationsConnectorService.java` and fixed `OperationsConnectorApi.java`, under `services/operations-core`. Existing Spring Security P03 JWT validation, current independently approved workforce grant check, transaction-level PostgreSQL cell/facility scope, FORCE RLS, maker-checker workflows, audit and outbox remain the authority. The independently administered SQL migration `V012__five_module_durable_connectors.sql` runs **after V011**, as a database migrator, and introduces 2 forced-RLS tables plus notification job lease metadata.

| Module | Resource and endpoint | Implemented invariants | Explicit remaining external integration |
|---|---|---|---|
| M21 procurement | POST `/api/v1/operations/connectors/M21/procurement-exports/{itemId}` and GET `/M21/procurement-exports` | Only internally matched/closed orders may enter immutable deduplicated outbox; unique item/version, exact currency/amount/receipt/invoice, SHA-256 snapshot, cell/facility RLS | Supplier ERP adapter, signed receipt verification and financial/GL acknowledgement. Export is **PENDING**, not posted or settled |
| M22 inventory | GET `/api/v1/operations/connectors/M22/barcode-stock?sku=...&lot=...&bin=...` | Exact SKU/lot/bin lookup, no invented item match, duplicates rejected, access to current RLS-authorized balance only. Prior PR #66 implemented immutable approved stock counts, stale-version guard and independent review | Hardware barcode capture, approved physical adjustments via signed separately issued evidence and stock journal; no scanner acts as an approver |
| M25 transport | POST `/api/v1/operations/connectors/M25/vehicle-reservations` and POST `/M25/vehicle-reservations/{id}/cancel` | Actual PostgreSQL exclusion constraint on overlap, hospital/facility isolation, max 24h window/90d horizon, idempotent reservation ref, current available vehicle/active dispatch check, explicit cancellation | GPS and vehicle registry, shift-aware future staffing, binding with actual ambulance dispatch workflow; reservation is not clinical transport acceptance |
| M29 configuration | GET `/api/v1/operations/connectors/M29/publications?afterAt=...&afterId=...&limit=...` | Stable `(published_at, publication_id)` cursor for duplicate timestamps, scoped publication version/digest. Complements the older timestamp-only feed; consumers should use new endpoint | Durable authenticated remote cell consumers, cache invalidation, independently audited apply + ack, conflict resolution |
| M37 notification delivery | POST `/api/v1/operations/connectors/M37/delivery-leases?limit=10` | Durable `SKIP LOCKED` claimant, fixed 90-second lease, 5-minute next retry, 20-attempt cap, no message body or credentials returned, no fabricated delivered state | TLS-authenticated provider adapters (SMTP/SMS/push), actual secret management, verified provider receipt evidence, escalations and safe redaction |

## Operational rules

Do not expose `/connectors` beyond the authenticated internal operations network. All calls require P03 OIDC JWT and current independent `WorkforceAdmission` permission revision, valid shift and hospital scope. Service account must use least-privilege runtime role; the schema owner/migrator MUST be separate.

**No false success:** Internal purchase match is not financial settlement. Barcode resolution is not physical count certification. A scheduled vehicle is not dispatched. A publication feed is not guaranteed applied in a remote cell. A claimed notification lease is not a successful SMS, email, push or patient acknowledgment.

M37 providers must obtain a real independent receipt as `external_evidence` and use the established M37 transition endpoint with maker-checker/proof validation to mark delivered; merely holding a lease must never change clinical notification status. Repeated lease claims use PostgreSQL row locks and a 90-second lease; worker crash leaves the job for later recovery, and a second claimant cannot reuse an unexpired lease.

The M25 overlapping reservation guard protects reservations at creation but the existing immediate dispatch workflow does **not yet enforce future reservations** at dispatch. Production scheduling/dispatch arbitration must bind both paths before live ambulance allocation. The database constraint alone is not a replacement.

The M21 export is immutable, and currently offers no external acknowledgement API. That is deliberate; independent finance acknowledgements cannot be fabricated from a browser request.

## Verification

CI: `.github/workflows/five-operational-integrations-ci.yml` validates V001–V012, exactly 25 FORCE RLS tables and runs the full Java25/PostgreSQL18.6 operations/mortuary integration and Java27 compile compatibility.

Tests: `OperationsConnectorPgTest.java` covers M21 export dedupe/unmatched denial, M22 exact barcode and collision deny, M25 conflict/idempotency/facility cancellation, M29 same-timestamp cursor and cross-facility RLS, M37 lease/retry/no-body/permission scope. Prior `OperationsIntegrationsPgTest.java` verifies the original five endpoints and all prior M27 source is preserved. All tests use synthetic fixtures only.

**Follow-on infrastructure must not be counted as installed:** real vendor contracts, managed OIDC, SMTP/SMS credentials, barcode devices, GPS providers, remote hospital cells and monitoring are absent in this repository's CI. A green source-build is not hospital readiness.
