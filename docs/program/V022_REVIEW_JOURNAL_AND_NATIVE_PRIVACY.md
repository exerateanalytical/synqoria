# v0.22.0 — Engineering review, evidence and release boundaries

## What was implemented

- **P05-B0038**: in-memory append-only review journal, two-event maximum, synchronized expected-version and duplicate-command checks; does not persist or authorize a clinical write.
- **P05-B0039**: independent history rebuild rejects scope drift, out-of-order or duplicate commands, digest tampering and revocation resurrection.
- **P05-B0040**: privacy-minimized integrity receipt derived only from a verified history. Not immutable external audit custody.
- **P05-B0041**: reviewer preflight checks current verified session, scope, policy revision, candidate revision and reviewer/registrar separation. Success is named `PRECONDITIONS_MET_NOT_AUTHORIZED`, not an approval.
- **P05-B0042**: package-private bounded JDBC read port, three SQL scope parameters, serializable connection requirement, query timeout and integrity validation. Uses an unapplied V009 draft; JDBC proxy tests do **not** establish PostgreSQL RLS or DB permissions.
- **P02-B0068–B0072**: native privacy lifecycle model, localized access messages, staff facility/role/shift scope presentation, proxy eligibility denial and accessible privacy announcements. Both native sources reference the models. No native binary, device or screen reader verification.

## Security and design review

All reviewer components remain internal, without production HTTP route, trusted IAM adapter, DB owner-role policy or registration release. Review event table V008 and V009 are **quarantined**: no permissive RLS policy or grants. V009 nullable digest columns are intentionally not production-ready until DBA-reviewed backfill/constraints. Any caller-provided `serverVerified` flag in a presentation helper is not an authorization decision and must never be wired directly to request input. App privacy curtain is a source-level model; OS snapshots and lifecycle behavior require real-device validation.

Warm Clinical palette, EN/FR and four bottom destinations are preserved. Mobile status text is non-PHI; no invented medical protocol or unapproved logo added. No canonical screen is marked implemented. Clinical release remains denied.

## Evidence

Ten individually executed commands and their exit codes: `evidence/batches/v220-P05-B0038.log` through B0042 and `v220-P02-B0068.log` through B0072. Full Java/Node/source/browser checks are in `evidence/batches/v220-*`. Historical v0.19–v0.21 checkpoint tests accept newer register versions without weakening old evidence assertions.
