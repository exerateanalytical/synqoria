# SYNQORIA v0.64.0 — Clinical inbox safety, stacked implementation

**Base:** GitHub `work/longitudinal-outbox-v063`, SHA `3caa48e16239907f45374db01f7ade5f4aa98908` (PR #6). The new branch `work/longitudinal-inbox-v064` is stacked on PR #6; it must not be merged directly into `main` before the parent stack is reconciled.

## Individually tested P07 engineering batches

- **P07-B0105**: immutable metadata-only event envelope with revision, digest and type bounds.
- **P07-B0106**: scoped consumer capability and expiring permit gate.
- **P07-B0107**: scope-qualified deterministic SHA-256 replay identifier.
- **P07-B0108**: monotonic sequence gate; matching-tip replay only, gaps and conflicting revisions denied.
- **P07-B0109**: time-boxed lease token and scope/candidate match.
- **P07-B0110**: bounded retry/backoff and mandatory park at 12 attempts.
- **P07-B0111**: time-boxed redrive with distinct requester and two approvers; host verifies their roles independently.
- **P07-B0112**: correlated durable commit proof requires inbox + projection + audit + outbox + transaction commit.
- **P07-B0113**: no broker ACK before complete commit proof; replay and failed-commit results distinct.
- **P07-B0114**: bounded privacy-safe receipt without patient identifiers, note bodies or exception text.

Each has an independent runnable Java test in `LongitudinalInboxSafetyTest`. Execute `bash tools/check_longitudinal_inbox.sh` to compile under Java 21 and run all ten individually. The test data and AtomicStore are **synthetic**; no Kafka listener, PostgreSQL tables, privileged role, OIDC verifier, KMS or live clinical data is introduced.

## Security and operational constraints

The producer-side outbox in PR #6 has at-least-once/uncertain-ACK semantics; this inbox foundation models deduplication and commit evidence but **does not implement a live Kafka consumer or PostgreSQL transaction**. `AtomicStore` is a contract: production integration MUST atomically verify RLS scope, monotonic sequence, the replay receipt, all audit/outbox writes, and the unique inbox row under a verified restricted SQL role. An untrusted caller MUST NOT construct `Permit` or `Redrive` without an upstream verified identity and grant. A future broker adapter must ACK only after durable proof and must never log patient metadata or sensitive broker exception text.

**Status:** source/unit-tested only. Clinical release denied. No screen or module certified. 40 modules/261 canonical screens unchanged; all 261 remain clinically unaccepted. Next P07 batch candidate B0115: restricted-role inbox JDBC adapter, schema + concurrency tests and real Kafka connector in controlled CI; do not count those as completed now.

## Verification provenance

Run-specific logs: `evidence/v640/individual-batch-results.json`, `evidence/v640/inbox-java-checks.log` and `evidence/v640/verification-summary.json`. The local inherited v0.61 source archive was used for Java compilation and UX regression because this runtime cannot clone the remote branch; Java production sources in that local archive compiled and its 292 Node/TypeScript tests passed. Those tests do **not** constitute a complete PR #6 regression or a production integration test. Formal certification remains deferred; deny-by-default safety is not waived.
