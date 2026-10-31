# SYNQORIA M03 v0.57.0 — Staff triage scoped read and workflow integration

**Status:** six internally tested implementation batches; release and formal certification are deferred, not bypassed.

## Implemented

- P06-B0363: HTTPS authenticated case summary GET, server-enforced patient+case route, exact summary decoder, no-store cache checks and response budget.
- P06-B0364: bounded revision-based audit history reader with exact fields, monotonic revisions, strict hash and operation validation, no notes.
- P06-B0365: volatile case session that aborts stale requests on patient switches, never caches identity or tokens, prevents concurrent writes and refreshes authoritative revisions.
- P06-B0366: explicit-patient worklist selection, at most 50 IDs, membership validation, cancellation and clean isolation on patient switches.
- P06-B0367: audit history pager for up to 1,000 entries held only in memory, bounded server pages and context invalidation.
- P06-B0368: OpenAPI GET case/history contract matching Java HTTP response shape and TS decoders.

Additional **source-only, NOT build-verified** M03 staff React case workspace built in `apps/web/components/TriageCaseWorkspace.tsx` with EN/FR text, accessible buttons, quick actions before history, and approved Warm Clinical semantic colors. It is opt-in via an authenticated session provider; the unauthenticated shell remains locked. This source-only UI component does not count as a seventh verified batch.

## Safety invariants preserved

The server remains the authority for tenant, facility, patient, actor permissions and action admissibility. The client cannot infer patient status or clinical priority, cannot queue offline clinical writes and cannot expose a record without a verified session. After any successful mutation, the client must reread the server case before allowing another action. Both transport and state layers deny/hide incorrect context or revision.

## Known engineering limitations

No installed Next.js or Expo dependency graph or device binary for this execution; no provider-backed production OIDC session; no live PostgreSQL restricted-role test; no runtime end-to-end browser access to authenticated PHI. No external certification, pilot qualification or clinical approval attempted at user direction. Keep deployment and clinical release denied until later separate review.

## Evidence

See `evidence/v570/batch-execution.json`, six individual focused logs, `check-ux.log`, `check-registers.log`, `check-source.log`, `check-openapi.log`, `javac-full.log`, `java-read-http.log`, source integrity manifests and cumulative ZIP SHA256.
