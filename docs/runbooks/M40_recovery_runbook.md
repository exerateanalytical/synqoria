# M40 reliability and recovery — operations runbook (engineering draft)

Not operationally accepted. Rehearse in a disposable environment first. Numbers shown by the console are measurements, never promises.

## Reading the health board
Healthy = fresh readiness and no warnings. Degraded = a warning/critical signal or a failing hard dependency. Down = readiness failing. **Unknown = no signal in the last five minutes** — treat it as a problem, not as good news.

## Declaring and running an incident
Declare (severity, service, impact) → acknowledge within the SLA (SEV1 5 min, SEV2 15, SEV3 60, SEV4 240) → assign an owner → add notes/actions (they are redacted) → escalate with a reason when mitigation stalls → mark mitigating/mitigated → **a different person** verifies recovery and resolves.

## Backups
1. The backup agent records the backup (kind, snapshot, WAL range, digest, key reference, retention). It is **Recorded, not verified**.
2. A different person submits the five checks (inventory, integrity with the digest recomputed from the artifact, key available, completeness, restore readiness) with evidence. Any failure — including a digest mismatch — makes it *Verification failed*; record a new backup, do not retry the same one.
3. Use the WAL continuity check before planning a recovery.

## Recovering
Plan (verified FULL base, target WAL position, outage start; mark *exercise* in disposable environments) → **authorize (different person)** → Fence (confirm egress disabled **and** writers stopped, with evidence) → Restore (restored position must equal the target) → Reconcile objects (none missing) → Recover audit (chain digest) → Reconcile external effects (none pending) → Begin validation → record the six validations → **reopen the named services (approver neither planned nor authorized)**.
Abort is possible until reopening; after a failed validation, abort and re-plan.

## After reopening
The console stores the measured time to reopen and the data-loss window for that recovery, labelled exercise or real. Do not quote them as targets.

## Never
Treat a creation success as a verified backup, reopen with an unreconciled external effect, reuse the planner as authorizer or approver, or edit tables by hand (triggers refuse).
