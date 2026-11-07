# SYNQORIA — P07 M04–M06 clinical outbox delivery v0.63.0

**Engineering build, NOT production authorization or clinical certification.**

## Implemented in this branch

- Typed, bounded, metadata-only event reference with scope, revision, command ID, event hash and event type. Patient UUIDs remain sensitive metadata even without clinical free text.
- A host-verifiable dispatcher permit requirement with facility/patient/encounter scoping and expiry. **Permit creation is not an authentication system**; production workload identity and scoped database permissions are missing.
- One-encounter-at-a-time ordered event claims, single-worker fencing with a random lease token, clock-derived 30-second claims, and optimistic acknowledgement with digest and scope checks.
- At-least-once semantics: success followed by a lost ACK triggers re-delivery; private Kafka consumers must deduplicate by event hash + command ID. No exactly-once guarantee.
- Bounded retry delays, permanent parking after attempt 12 and a blocked subsequent revision until an authorized reconciliation process is implemented. Expired final leases become parked.
- JDBC transaction-local scope echo, short timeouts, bounded seed/projection, ordered `FOR UPDATE SKIP LOCKED` claim, CAS ACK and retry; broker publish is outside the DB transaction.
- V021 quarantined schema has enforced RLS, no runtime policies, grants, broker configuration, KMS or SQL execution evidence. It cannot be deployed with operational permissions yet.

## Executed evidence

- 11 individually executed Java/JDBC/unittest scenarios, `evidence/v630/P07-B0094.log` through `P07-B0104.log`.
- 17/17 source, security, historical clinical, OpenAPI, schema, migration and UX regression groups.
- 65 synthetic delivery assertions and 175 synthetic JDBC assertions. A fake transactional JDBC protocol does NOT demonstrate PostgreSQL row locking, isolation or row-level security.
- Historical M04/M05/M06 domain and HTTP tests re-passed; canonical module 40, screen 261, clinical acceptance 0.

## Open integration gates (do not silently bypass)

1. Provision a real PostgreSQL database, restricted roles and evidence-backed RLS for V020/V021 under separate reviewed migration.
2. Verify row-level tenant/facility/patient/encounter isolation and concurrent workers under PostgreSQL, not a proxy.
3. Bind dispatcher permits to mTLS/OIDC workload identity with revocation; require a private broker with no clinical free text in logs.
4. Implement consumer-side inbox deduplication, DLQ review/maker-checker redrive, observability with privacy filtering and alarm policies.
5. Perform deployment/mobile builds and required clinical and independent security certification later.

**Next candidate:** P07-B0105, check fresh GitHub/main/PR stack and other-chat reservations first. Parallel P06 starts B0392 if still unreserved.
