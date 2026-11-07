# ADR-M40: Reliability, backup verification and governed recovery

Status: proposed (engineering candidate, 2026-11-07).

## Context
A hospital cell must be able to see what is unhealthy, trust that its backups can be restored, recover to a defined point, and reopen only when clinical and financial effects are reconciled. Recovery claims have repeatedly failed in practice because a "successful" backup job was treated as proof.

## Decisions
1. **Unknown is not healthy.** Health is derived only from fresh signals (5 minutes). No fresh READINESS signal means UNKNOWN. A HARD dependency that is DOWN or UNKNOWN caps a service at DEGRADED.
2. **Telemetry is redacted before it is stored,** and reading telemetry is itself logged. Redaction is privacy-first (key deny-list, e-mail, bearer tokens, long numbers, free text); over-redaction is preferred to leakage.
3. **A backup is RECORDED, then VERIFIED.** Verification is five checks by a different person; INTEGRITY can pass only when a digest recomputed from the artifact equals the recorded digest, regardless of what the client claims. A trigger refuses VERIFIED without five passes. Backup facts are immutable. Key material is never stored, only a reference.
4. **Recovery is a line, not a flag.** Plan requires a VERIFIED, unexpired FULL base backup and continuous WAL ranges (verified archives only) up to the target; authorization by a different person re-checks both; steps need positive evidence (fencing needs egress disabled *and* writers stopped; restore must equal the target position; objects and external effects must be zero-missing/zero-pending; the audit chain digest is recorded).
5. **Reopening is scoped and independent.** Six validation items must all pass (database trigger), the approver neither planned nor authorized the recovery, and only the named services reopen.
6. **Measure, never promise.** RPO/RTO rows are written only at reopening, from the application clock, labelled EXERCISE or REAL. No target is stored anywhere.
7. **One real recovery per cell** may be open at a time; exercises are independent.

## Consequences
* The system cannot be talked into believing a backup or a recovery succeeded.
* It depends on external agents for facts (digests, positions, reconciliation); their evidence is as trustworthy as the agents. Rehearse with real PITR before publishing any objective.
