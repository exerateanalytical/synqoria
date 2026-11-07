# P07 M04–M06 — Quarantined encrypted persistence design (v0.62.0)

This checkpoint adds source implementations and simulated JDBC contract tests, NOT a production-ready store.

## Consistency model

- `ClinicalCaseCore.Persistence.transaction` loads one tenant/facility/patient/encounter aggregate while row-locked.
- JDBC begins a transaction, configures bounded statement/lock timeouts, installs transaction-local `app.*` scopes and checks all echo fields.
- `LongitudinalSnapshotCodec` deterministically stores all note/observation/group, signed/corrected and audit/outbox/receipt state. 4 MiB hard limit, bounded counts/strings, duplicate rejection, UTF-8 rejection and re-verification of a linked digest chain on decode.
- `LongitudinalEnvelopeCrypto`: AES-256-GCM random nonce with tenant, facility, patient, encounter, aggregate revision and KMS key ID as authenticated context. Rotations require reviewed key policy, key-life-cycle provider and separate evidence; no keys embedded in source.
- `LongitudinalJdbcPersistence`: SELECT ... FOR UPDATE on scoped composite key, decrypt and audit evidence correlation; CAS (`revision=?`) for aggregate update; linked audit, receipt and outbox rows in same SQL transaction; errors roll back. A DB-level append-only audit trigger protects historic events/receipts once deployed.
- Server-only `LongitudinalJdbcProvisioning` requires trusted patient-scoped `encounter.provision` privileges and creates a zero-revision PLANNED state idempotently (duplicate fails), not a clinical admission or treatment authorization.
- `LongitudinalClinicalWiring` permits injection of externally verified identity, clinical evidence, DataSource and KMS. No server, identity provider or demo store is auto-installed.

## Quarantine conditions

- `V020__longitudinal_secure_quarantine.sql` is a design migration; RLS is **ENABLED and FORCED** on all four protected tables, and PUBLIC privileges revoked. **No runtime role GRANT, CREATE POLICY, BYPASSRLS or permissive fallback**. Actual role grants, scope attestation and RLS tests require a separate reviewed migration.
- Tests use Java's standard JDBC interfaces in a synthetic recorder; no PostgreSQL server/driver is available in this execution. Do not report database integration qualified or production deployment.
- KMS integration, secure provisioning of encounter identifiers, facility trust, data retention/legal basis and real PostgreSQL concurrency need further integration. PostgreSQL commit-on-timeout and outbox delivery require separate operational recovery and reconciliation design.
- All clinical decisions are human-entered; no automatic diagnosis, priority, dosage or discharge medical decisions. Formal certification deferred. Release denied.
