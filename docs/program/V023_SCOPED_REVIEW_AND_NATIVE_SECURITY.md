# SYNQORIA v0.23.0 — Engineering review and implementation limits

## Continuity

Started from v0.22.0 conversation-attached cumulative ZIP because the latest verified persistent Library checkpoint was v0.21.0 and the v0.22.0 Library upload was quota-blocked. Independently verified the v0.22.0 SHA-256 checksum, ZIP CRC and all 671 internal file hashes before changes. v0.21.0 was materialized from `/SYNQORIA` and independently hash-matched to its Library checksum. No historical source was overwritten.

## P05-B0043–B0047: review-event integrity v2

- V2 domain-separated digest cryptographically binds tenant, facility, review, command, event version, kind, binding digest and previous digest. Prevents a V1-style chain from being silently transplanted to a different review scope.
- Independent V2 history verifier rejects scope mismatch, duplicate commands, version gaps, invalid state transitions, digest mismatch and histories longer than two events.
- Receipt derivation requires verified V2 history and exports only schema, head digest, version and revoked flag.
- Current-state preflight rejects revoked decisions, changed IAM grant epoch, stale review versions and absent session evidence; a positive response is named `PRECONDITIONS_ONLY` and cannot authorize clinical writes.
- Immutable allowlisted export excludes tenant/patient/review identifiers and free-form clinical content. **No external audit custody or irreversible proof is established.**

**Compatibility boundary:** These V2 classes do not read V1 SQL rows and are not connected to any clinical registration writer. An explicit versioned SQL migration, DBA approval, backfill strategy and restricted-role RLS tests are required. The existing V009 quarantine remains unapplied.

## P02-B0073–B0077: native safety boundaries

- Strict deep-link root allowlist rejects query strings, fragments, percent encoding, traversal and unverified sessions. Backend authorization remains mandatory.
- Staff facility/role/shift switch invalidates volatile draft state and increments epoch; real encrypted device storage is not implemented.
- Async request coordinator aborts pending requests on scope/background change and refuses late responses. Native sources call invalidation on background.
- Critical drug/dose/allergy typography contract declares wrap, scaling and 48px minimum; not yet rendered in a clinical screen, not device-tested at 200%.
- Offline clinical write queue always denies enqueue and replay, including while online; only nonclinical locked shell is available.

Both staff and patient React Native source shells were wired to the locked deep-link, stale-request and offline-write policies. The patient shell's missing imports for existing privacy components were corrected. No native binary, clinical screen or authenticated session is produced by this work.

## Gates and test limitations

Ten focused batch checks pass; full Java security regression, TypeScript compilation, Node tests, Python source/register checks and static Chromium specimens are run separately and their outputs saved under `evidence/batches/v230-*`. Static Chromium checks are NOT Next.js or Expo builds. No PostgreSQL execution, OIDC provider integration, mobile emulator/device tests, independently reviewed clinical policy, independent security audit or pilot qualification. All 40 canonical modules and 261 screens retain their original IDs and remain unimplemented/uncertified. Clinical release and registration writes remain denied.

## Next

P05-B0027 remains blocked on actual PostgreSQL restricted roles/RLS and durable transactional audit/outbox integration. Next independent source-level candidate: P05-B0048 V2 versioned SQL migration contract with explicit DBA hold and negative privilege tests. P02-B0078 native accessibility wiring and compiled device tests when build environment is available.
