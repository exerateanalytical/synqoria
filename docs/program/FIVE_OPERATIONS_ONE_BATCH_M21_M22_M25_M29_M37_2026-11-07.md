# SYNQORIA 2026-11-07 — ONE-BATCH M21 / M22 / M25 / M29 / M37 integration

This is a **single coordinated integration** of five existing administrative modules, not five newly created modules. It preserves M31, M23, M27 and all clinical services. Claude's M38 implementation and forensic reconciliation remain independently owned.

## Source and endpoints

`services/operations-core/src/main/java/org/synqoria/operations/OperationsIntegrationService.java` reuses the existing `OperationsStore.scoped()` transaction and least-privilege PostgreSQL runtime role. The fixed REST controller `OperationsIntegrationApi.java` requires the established P03 signed OIDC resource-server token **and** current, independently provisioned workforce grant revision/shift:

| Module | Verified endpoint | Implementation | Remaining external work |
|---|---|---|---|
| M21 procurement | `GET /api/v1/operations/integrations/M21/procurement-reconciliation?limit=50` | RLS-bound real PO/receipt/invoice reference, amount and currency state feed; no fabricated invoices or payments | Contracted supplier/finance ERP synchronization, independently checked accounting attestations |
| M22 stock | `POST /api/v1/operations/integrations/M22/stock-counts/{itemId}`; `POST /M22/stock-counts/{countId}/review` (same prefix) | Lot/bin-bound physical count against locked authoritative stock balance; idempotent maker input; **different checker required**; old stock version denies; audited count row immutable in V011; variance is a finding and **never auto-adjusts stock** | Barcode scanner/mobile UX, approved adjustment-command with external evidence and explicit stock ledger movement |
| M25 transport | `GET /api/v1/operations/integrations/M25/available-vehicles?limit=50` | Real current vehicle state + active-trip exclusion under hospital-cell RLS | Vehicle/driver registry, GPS telemetry, future reservation calendar, clinical transport handover |
| M29 configuration | `GET /api/v1/operations/integrations/M29/publication-feed?after=<ISO-timestamp>&limit=50` | Approved publication history with schema version, content digest, author, supersession; bounded feed | Authenticated multi-cell push/consumer, version-acknowledged propagation, retry/broadcast transport, full deterministic cursor for identical timestamps |
| M37 notifications | `GET /api/v1/operations/integrations/M37/due-deliveries?limit=50` | Due scheduling and retry ceiling over persisted queue; excludes future jobs, payload bodies and secrets | Configured and independently authenticated SMTP/SMS/push adapters, lease-based worker, delivery receipts and escalation acceptance |

## Migration

`services/operations-core/db/migration/V011__m22_scoped_stock_count_review.sql` creates an independent stock-count event table with FORCE RLS, immutable maker facts, non-self reviewer requirement, bounded counts and replay uniqueness. Apply via **separate DBA migration role** after V001–V010, never via runtime. Do not treat review as stock correction or automatic financial posting.

## Invariants and warnings

1. Every request is admitted through `WorkforceAdmission.requireAuthoritativeGrant()`. The signed JWT alone is insufficient; role, current roster grant, facility, shift and grant revision must match.
2. Runtime role `synqoria_operations_runtime` must not own tables or bypass PostgreSQL RLS. The service checks this on every scoped physical transaction.
3. M22 never mutates `stock_balances` in the cycle-count path. A quantity mismatch is explicitly not evidence to issue, return or adjust anything.
4. M21 responses describe **internal workflow records**, not externally verified cash or supplier settlement.
5. M25 is immediate availability only; without approved GPS/dispatch providers it cannot claim a real ambulance's location or readiness.
6. M29 uses ISO timestamp cursors that are not exactly-once across identical publication instants. Before production cross-cell replication, add a compound `(published_at, publication_id)` cursor and a durable consumer checkpoint.
7. M37 returns **delivery candidates**, not messages sent; provider credentials and independent attestations are deliberately absent from source. Do not label QUEUED as DELIVERED.
8. All external integrations remain disabled unless valid production secrets/agreements and end-to-end acceptance are provisioned.

## Evidence and CI

One single PR #66, code and synthetic integration tests in `OperationsIntegrationsPgTest.java`. GitHub Actions `five-operational-integrations-ci.yml` runs Java25 and the **full** V001–V011 migration chain on disposable PostgreSQL18.6, all existing operations and mortuary unit + integration tests, plus Java27 compatibility. No synthetic tests imply hospital production use.

## Next path without duplication

Keep Claude's PR #56 on M38 and Issue #60 on 40-module forensic reconciliation distinct. All five module source paths remain under integration lead until this PR is merged and the automatic `current-platform-reconciliation.json` update confirms source provenance. After this batch, wiring actual external systems is independent work requiring approved providers, secrets, environments, contract tests and explicit deployment gates. Certification remains a separate later stage.
