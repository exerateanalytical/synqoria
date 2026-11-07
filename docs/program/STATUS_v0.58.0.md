# SYNQORIA v0.58.0 — M03 scoped workflows (engineering only)

**3 meaningful batches completed out of requested 60 (5%); 57 uncompleted (95%).** Next unreserved candidate P06-B0372.

- P06-B0369: verified staff scope and worklist/case coordinator, context scrubbing, server revision reread.
- P06-B0370: clinical response size/content/cache validation and cancelled-session request fencing.
- P06-B0371: idempotent arrival workflow with uncertain acknowledgement/replay and case reread.

**Observed local verification:** Node focused tests 10/10 + 6/6 + 8/8; full UX suite 224/224; source checks passed; canonical register tests preserved exactly 40 modules and 261 screens; Java 363 source/test files compiled; status matrix release-denied check passed. Tests use synthetic transport. No live PostgreSQL, OIDC/MFA, Next.js/Expo binary/device evidence or clinical approvals claimed.

**GitHub provenance:** isolated branch work/triage-scoped-workflows-v058 based on 9fdbf95f0ad85cc3867185acfbd19b00be8d80d1, created after the prior v0.57.0 code publication and subsequent main-branch historical scheduling recovery merge; the newer recovery source is preserved through the branch base tree. Cumulative local ZIP derives from v0.57.0 and should NOT overwrite the Library current checkpoint without reconciling newer main-only files. GitHub branch is the authoritative full merged source for these changes until PR merge. Formal certification deferred, no clinical release.
