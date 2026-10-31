# SYNQORIA M03 — v0.52.0 restricted triage persistence checkpoint

**Status:** Internal source and synthetic JDBC-protocol tests only. **NOT clinically approved, deployed, or tested against live PostgreSQL.**

## Source authority and preserved contracts

The starting `/SYNQORIA/SYNQORIA_CURRENT.zip` has SHA-256 `310a504a3f6233c3ce579e8c7a39ca5823c5c27b9af5b80a5114ec6b09e15de8`. All 1,460 original manifest entries were independently rehashed before changes. The authoritative architecture/UI documents, 40 modules, 261 screens, and phase sequencing remain in place. No screen is marked clinically accepted.

## Implemented code

- `TriageSnapshotCodec`: versioned deterministic, bounded binary aggregate snapshots with strict parsing, duplicate-key rejection and hash-chain verification. No Java native object deserialization.
- `TriageEnvelopeCrypto`: AES-256-GCM with random nonce and AAD binding tenant, facility, patient, case, revision and key identifier. Keys are injected by the caller; no production KMS exists.
- `TriageJdbcScope`: transaction-local `set_config(..., true)` for backend-derived tenant/facility/patient/actor, with four-column echo verification.
- `TriageJdbcFence`: fail-closed SERIALIZABLE, read-write, autocommit-off transaction, pinned restricted PostgreSQL role, no superuser/BYPASSRLS, three active RLS tables, and bounded SQL timeouts.
- `TriageJdbcStore`: patient-scoped `SELECT ... FOR UPDATE`; encrypted row decode, row/snapshot consistency, persisted event-tip/outbox integrity, domain transition verification, revision compare-and-swap, event and outbox writes in the same caller-owned transaction, rollback on any failed write, and commit only after all writes. Requires trusted access/evidence and both manage and action-specific capabilities.
- `V016__triage_encrypted_snapshot_quarantine.sql`: encrypted columns, size/digest/atomic-null checks, append-only event/outbox triggers, RLS ENABLE/FORCE, PUBLIC REVOKE. **No GRANT and no CREATE POLICY**. Not executed on PostgreSQL.
- Static migration adversarial gate and 16-entry immutable migration SHA-256 lock; historical checksum tests updated to dynamically count locked migrations.

## Verification boundary

All 340 Java main/test sources compiled. The 45 new individual synthetic Java scenarios passed. The previous 60 triage feature tests were rerun against the updated classes and passed. The prior synthetic triage journey passed. UX-core passed 109/109 tests. Navigation/canonical registers and native source checks passed. Nine new V016 static adversarial tests, seven existing V015 tests and six migration-checksum tests passed. Source-only checks do not establish a functioning database, app, API, encryption key management or clinical safety.

## Open release blockers

1. **P06-B0169:** create an authorized, auditable encrypted intake/genesis insert and outbox entry; the current mutation adapter cannot create its first row. No production grant or migration approval.
2. Execute the V015/V016 SQL against isolated PostgreSQL, test restricted roles, RLS cross-tenant/patient isolation, serialization retries, concurrent writes, actual rollback and outbox behavior. No PostgreSQL executable/driver was available in this environment.
3. Integrate production KMS, key IDs/rotation, encrypted backups and data-retention controls. Synthetic tests use a fixed test-only key.
4. Connect independently validated OIDC/MFA/shift/consent evidence to an authenticated HTTP gateway and audited application transaction boundary. The trusted context classes alone are not an identity provider.
5. Build and test staff/patient native clients, browser workflows, accessibility/EN-FR responsive screens and device integration.
6. Obtain clinical safety review, hazard signoff, security penetration testing, supervised pilot approval and acceptance. **Clinical release denied; 0/261 screens and 0/40 modules accepted.**

## Exact next batch

**P06-B0169** — fail-closed authenticated intake/genesis persistence with idempotency and first-event/outbox linkage, plus restricted-role PostgreSQL execution tests where infrastructure is available.
