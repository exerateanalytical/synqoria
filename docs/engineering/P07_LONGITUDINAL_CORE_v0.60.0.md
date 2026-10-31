# SYNQORIA P07 — M04, M05, M06 internal clinical domain v0.60.0

## Scope and truthful implementation status
This code implements 60 independent *domain operations* with executable tests, **not 60 completed clinical screens, operational services or certified hospital modules**. Cross-phase foundational implementation is allowed without advancing P06 certification. Source branch is stacked on GitHub PR #2. The local ZIP descends from v0.59.0 and may omit later main-only recovery/project memory files: **do not replace Library CURRENT or overwrite main from this ZIP**.

## M04 encounters (P07-B0001–B0020)
Authorized, revision-scoped checkin, activation, care team assignment and removal, responsibility, documented admission request/approval, bed *proposal only* (not occupancy), room and location, transfer request/acceptance/cancellation/recording, leave/return, discharge planning and an explicitly authorized discharge state change. Cross-facility transfer and actual bed allocation are NOT implemented. Admission and transfer authorizer must differ from requester.

## M05 notes (P07-B0021–B0040)
Clinician-entered draft content, sections, diagnoses (never auto-classified), problems, references, confidential flag, reviewer separation, review/readiness, authenticated-internal signing, immutable signed content, linked amendments, entered-in-error status with original content preserved. Additional internal operations include co-sign requests and co-signatures. No external e-signature provider or deployed authentication is claimed.

## M06 observations (P07-B0041–B0060)
Typed numeric/text/categorical capture, device and provenance, contextual metadata, separation-of-duty verification, rejection, recheck request, correction lineage, invalidation, grouped set verification and bounded authorized timeline reads. Units are explicit, and no clinical thresholds, drug doses or medical priority protocols are generated. Full unit terminology validation remains deferred.

## Command security and persistence boundary
- Verified server-issued Scope and Actor (never asserted from browser JSON) must be passed by the hosting API.
- Strict per-action input keys, bounded clinical text, expected revision, UUID idempotency command, per-action privileges, clinician intent, audit hash chain and state digest.
- Persistence interface requires one scoped atomic DB transaction including row lock, aggregate save, event, outbox and idempotency receipt. Only synthetic test-store adapter exists; it is located under Java test source and **must not** be deployed.
- For production, implement PostgreSQL tenant/facility/patient RLS, scoped row locking, a stable append-only audit schema, atomic outbox, KMS encryption, validated clinical terminologies, FHIR mappings and OIDC session ingress. These remain unverified blockers.
- No API endpoints or mobile/web routes are enabled for these operations yet; formal clinical certification is deferred but mandatory release gates remain DENIED.

## Evidence
Run `python tools/run_v600_batches.py` after compiling Java to an available classes directory. It executes **60 separate JVM processes** using `-ea`, each printing one verified action and exit code. Negative-path tests are in `ClinicalBoundaryAdversarialTest.java`, which includes scope mismatches, stale writes, replay key reuse, audit corruption, reviewer separation, numeric refusals, atomic rollback and cross-thread revision conflict.

## Next work
P07-B0061: authenticated HTTP + transactional JDBC integration tests, with tenant/patient RLS and terminology authority. P06-B0392 remains available for the separate triage workstream. Do not infer that phase P07 is certified or completed.
