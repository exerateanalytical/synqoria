# SYNQORIA — GitHub reconciliation inventory (2026-11-05)

Status: **reconciliation in progress; NOT an integrated release or certification**.

## Verified repository observations
- Repository: exerateanalytical/synqoria; main at `96d45960e2b7ad0039818de259b2782d2cbca86f` when inspected.
- P06 draft PR #1: work/triage-scoped-workflows-v058; P06-B0369–B0371.
- P06 draft PR #2: work/triage-safe-command-recovery-v059; P06-B0372–B0391; stacked on PR #1.
- P07 draft PR #3: work/clinical-encounter-documentation-v060; P07-B0001–B0060; stacked on PR #2.
- P07 draft PR #4: work/clinical-http-integration-v061; P07-B0061–B0070; stacked on PR #3.
- P07 draft PR #5: work/longitudinal-secure-persistence-v062; P07-B0071–B0093; stacked on PR #4.
- Later existing branch: work/longitudinal-inbox-admission-v067 at `b0a1495462f76cc97c57201006bd58ee5e7a1600`.
- Compare main...v059: diverged, ahead 16, behind 22; main...v062: diverged, ahead 19, behind 22; main...v067: diverged, ahead 70, behind 22. These are Git commit counts, NOT engineering batch counts.
- Library /SYNQORIA/SYNQORIA_CURRENT.zip observed as version 10, size 4,291,719 bytes; its age and content must not supersede Git history.

## Preservation and integration rules
1. Keep main unchanged until file-by-file conflict resolution and tests pass. Never force-push or overwrite a cumulative checkpoint based on version labels.
2. Preserve canonical 40 modules, 261 screens, docs/authority architecture/UI, patient/tenant isolation, audit/outbox and clinical/security gates.
3. Diff latest v067 against main, inspect all main-only 22 commits, and identify overlapping paths. Reconcile migrations and batch IDs before integrating source.
4. Validate Java 21, source/register checks, UX tests, migrations, clinical isolation and CI against actual merged tree. Do not count historical tests as newly executed.
5. Merge through reviewed PR after green checks. Publish exact merge commit, new cumulative ZIP and SHA-256 only after independent verification.
6. The P06 candidate remains B0392 until a fresh ledger confirms availability; the P07 next candidate must be read from latest v067 handoff (do not restart at B0094).

## Current execution
- New implementation batches: **0/60** (0%; 60 remain).
- New merged production code: none. GitHub branches already contain work; not yet reconciled into main.
- New tests executed: none. Clinical release: DENIED. 0/261 screens accepted; 0/40 modules certified.
- No claims of live PostgreSQL/RLS, production OIDC/MFA, KMS, Next/Expo build or clinical approval.

This record is an audit-safe coordination checkpoint, not a substitute for the requested full integration.
