# M39 fleet control plane — operations runbook (engineering draft)

Not operationally accepted. Rehearse every procedure in a disposable environment. No step here creates infrastructure; executors do, and report back.

## Provision a tenant
1. Request (requester) → Approve (a different person) → run the external executor → record its result: *OK* creates the tenant and a PROVISIONING cell; *failed* records the failure code and creates nothing.
2. Qualification screen: start, record the eight checks with evidence references, complete (a different person). Only then can the cell be set ACTIVE.
3. Before the cell has ever served traffic, *Roll back* retires it. After that, rollback is refused (`irreversible`).

## Release to the fleet
Create manifest (digest + schema range) → approve (different person) → start rollout with a canary stage and the remaining cells. Record deployment evidence per cell (the reported digest must equal the manifest). Wait for post-deployment HEALTHY observations, then advance. A failed deployment or an UNHEALTHY report from a deployed cell stops the rollout; stopped rollouts are final — start a new one.

## Migrate a cell
Plan (source ACTIVE, target QUALIFIED, same tenant, residency respected) → Copy → Catch up (lag ≤ 5 s) → Freeze → Fence → Verify (digests equal) → **Switch (approver ≠ planner, current epoch)** → Observe → Retire (target must report HEALTHY after the switch).
Rollback: before the switch it restores the source to ACTIVE. After the switch it needs an approver and reverses the route with a new epoch. After *Retire* nothing can be rolled back.

## When a route is refused
`route_unavailable`: the serving cell is not ACTIVE or its last health observation is missing, UNHEALTHY or older than five minutes. Fix the cell or fail over with *routes → switch* (expected epoch required).

## Never
Edit tables by hand (triggers refuse), share a database or credential between cells, or treat a missing health observation as healthy.
