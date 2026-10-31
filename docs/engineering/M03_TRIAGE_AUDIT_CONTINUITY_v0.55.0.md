# M03 triage arrival and audit-ledger continuity — v0.55.0

**Status: implemented and internally tested, NOT production or clinical certified.**

## Model and invariants

- Revision one is the separately persisted `ARRIVAL` event with tenant/facility/patient/case scope, command identifier, authenticated actor evidence and arrival timestamp. The genesis anchor is stored separately from revision-2+ changes in the aggregate and encrypted version-2 snapshot.
- The first subsequent command MUST use the revision-one genesis digest as its previous digest; all later events chain to the preceding digest. No clinical priorities are inferred or generated.
- Version-1 snapshots can be parsed for continuity analysis, but a live scoped JDBC read rejects an unanchored snapshot. A clinical record's historical evidence MUST NOT be invented to satisfy migration.
- Before a mutation or replay, locked JDBC reads check the full chain against protected, patient-scoped event, receipt and outbox rows. Any missing genesis, gap, wrong actor, forged digest, missing receipt/outbox or conflicting row fails closed.
- The V019 quarantined migration defines exact scoped event/revision/digest foreign keys for receipts and event/digest keys for outbox rows. It enables and forces RLS while granting no application access.
- Patient-scoped, verified-session GET summary/history transport emits only allowlisted structured fields; history is capped at 100 entries per page with an explicit `afterRevision` cursor. No free-text notes are returned; all responses have no-store headers.

## Boundaries

`TriageJdbcPersistence` is not deployed and restricted-role tests have NOT been executed. Existing V015/V016/V017/V018 migration layering remains unreviewed for production. Encryption uses application-managed keys only in synthetic tests, not a production KMS. Clinical data migration needs supervised retention and provenance checks. Production OIDC, rate limiting, mobile clients, clinical approvals and pilot security qualification are absent.

## Tests and evidence

- `TriageAuditContinuityTest`: 45 synthetic assertions covering genesis, rev2 links, replay and adversarial tampering.
- `TriageReadHttpIntegrationTest`: 45 loopback assertions for scoped access, safe failure, history, 100-record paging and escaping.
- `test_triage_correlated_migration_gate.py`: 12 static adversarial V019 gate tests.
- `test_triage_jdbc_evidence_contract.py`: 8 static JDBC source-contract checks (NOT a database test).
- See `evidence/v550/verification-summary.json` and exact per-command stdout logs.

**Clinical screen acceptance: 0/261. Module certification: 0/40. Release status: DENIED.**
