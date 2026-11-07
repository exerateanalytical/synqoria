# SYNQORIA v0.59.0 — M03 restricted staff operations (implementation checkpoint)

**GitHub base:** PR #1 branch `work/triage-scoped-workflows-v058`, commit `b7aeba143f4da105ce8c728faffdbeda160f4d63`; this branch inherits the scheduling recovery merge from main as of PR #1 creation. Separate new branch `work/triage-safe-command-recovery-v059`; no merge to main without review.

**Requested:** 60 meaningful implementation batches in this execution. **Completed: 20 verified feature-level bundles**, P06-B0372 through B0391 (33.33%); **not completed: 40** (66.67%). 20 are separate substantial workflow responsibilities, not a split of test assertions. Explicitly no certification, patient screens accepted or live PostgreSQL claims.

**User-visible release gate:** NOT CERTIFIED; 0/261 accepted clinical screens, 0/40 clinical modules certified. Formal certification is deferred, not bypassed.

**Application-layer implementation:** Volatile idempotent command recovery; permission leases; observation corrections; queue operations; clinician assignment and handover; safety holds; escalation and reassessment; patient-scoped read batching; 1,000-event-bounded audit navigator; clinician-entered priority and arrival evidence; native privacy lifecycle; bounded audit search; high-impact human confirmations; HTTP command journey and unified staff worklist/case workflow. All decisions remain clinician-entered and server-side permissions authoritative.

**Scope:** 40 modules, 261 screens remain canonical. These are staff-side functionality, not new accepted screen IDs.

**Tests:** See `evidence/v590/batch-results.json` and `evidence/v590/*.log` for individual commands/exit status, including failed intermediate preflight results retained. Next/Expo compiled builds, deployed HTTPS/OIDC/MFA, PostgreSQL RLS/concurrency and clinical approval remain unavailable.

**Source authority warning:** This local full ZIP descends from the v0.58.0 local checkpoint and may lack newer GitHub-only recovery and project memory files; the GitHub PR branch is the integration authority and must be reviewed against current main before merge. Do not overwrite shared Library `SYNQORIA_CURRENT.zip` using a local ZIP without full reconciliation.

**Next candidate:** P06-B0392, subject to checking new GitHub commits and other-workstream reservations. No background work or unrelated branch changes claimed.
