# ADR-M39: Tenant and hospital-cell control plane

Status: proposed (engineering candidate, 2026-11-07).

## Context
Hospital cells are operated independently. A shared management plane must provision, qualify, release to and move cells without becoming a second source of truth for identity, patient data or authorization, and without ever sharing storage or credentials between cells.

## Decisions
1. **Management plane ≠ hospital cell.** The control plane stores tenants, cells (with opaque database/credential references), lifecycle state, routing, release rollouts and migrations. It stores no clinical data and no secrets. `synqoria_cell`/`synqoria_facility` claims identify the management plane here.
2. **Tenant scope is enforced twice.** Tenant-scoped operators carry `synqoria_tenant_scope`; the service rejects other tenants and PostgreSQL forced RLS repeats the check. Platform-wide operators have no such claim. Fleet-global objects (manifests, rollouts) are platform-only.
3. **Declarative, idempotent provisioning with independent approval.** One request per tenant key; storage isolation is always dedicated (database or cluster). The service records the external executor's result; on failure nothing is created.
4. **Lifecycle is a table.** PROVISIONING→QUALIFIED→ACTIVE→DRAINING/FENCED→MIGRATING→RETIRED (plus FAILED). The same table is enforced in Java and by trigger; ACTIVE requires a passed eight-check qualification completed by a different person.
5. **Routing fails closed.** A decision is issued only for an ACTIVE cell with a recent HEALTHY/DEGRADED observation, is signed (HMAC-SHA256), short-lived and carries the placement epoch and directory version. Placement epochs only rise; a switch must present the epoch it saw.
6. **Releases need evidence, not assertion.** Deployed means the reported digest equals the approved manifest digest; stages cannot be skipped; each stage needs post-deployment HEALTHY observations; failure or an unhealthy deployed cell stops the rollout automatically.
7. **Migration is a line with a point of no return.** Evidence at every step; verification requires equal digests; the switch is done by an independent approver with the current epoch; rollback reverses the route with a new epoch until the source is retired, then it is impossible.

## Consequences
* Safe by construction against shared credentials, silent epoch regressions and unverified deployments.
* Real provisioning, copying and deployment remain external; the console is only as truthful as the evidence executors report.
