# v0.25.0 — Reviewer persistence adapter and accessible navigation engineering review

## Engineering intent and delivered source

P01-B0033 introduced a migration safety auditor that checks monotonic migration numbering, required table definitions, FORCE/ENABLE RLS, PUBLIC revokes, and forbidden grants/destructive statements. Negative mutation fixtures verify the checker fails on removed FORCE RLS and added GRANT. It is **not a PostgreSQL SQL parser** or migration execution substitute.

P01-B0034 added a sanitized read-only `psql` readiness probe with a pinned role, PostgreSQL >=16, RLS setting, non-superuser and non-BYPASSRLS checks. Unit tests use a synthetic runner. The real probe returned `missing_connection_or_pinned_role`, exit 4; no DB credentials or runtime were present. The probe does not establish table-specific RLS acceptance.

P01-B0035 added a dependency/lockfile readiness audit for separate web, staff mobile and patient mobile apps. Synthetic installed-dependency fixtures passed; actual workspace check returned missing root lockfile and Next/React/Expo dependencies, exit 4. npm registry DNS EAI_AGAIN was independently observed. No Next.js or Expo compilation occurred.

P02-B0084 implemented an accessible nonclinical **More** button as the fourth mobile destination in the Next shell source, with `aria-expanded`, `aria-controls`, Escape close/focus return, bilingual language controls and no unauthorized module links. The static interactive specimen was updated and tested in Chromium. P02-B0085 tested the specimen at 320, 360, 390, 412, 768, 1024, 1280, 1440 and 1920 pixels, plus French/200% scaling; screenshots are in `evidence/batches/`. **This does not establish compiled Next.js behavior, physical device accessibility or UX01–UX10 certification.**

P05-B0053 introduced the **unapplied** V010 composite-key review audit table linking review events and outbox events; it has ENABLE/FORCE RLS, no allow policies, and PUBLIC revocation. P05-B0054 added a package-private JDBC metadata check for PostgreSQL, restricted role and FORCE RLS on four tables. P05-B0055 added scoped, bounded `FOR UPDATE NOWAIT` reviewer decision locking and history reading. P05-B0056 added package-private event/outbox/audit append on the same connection with exact row counts and rollback on partial failure. P05-B0057 added internal orchestration that rechecks role, row lock, event state, version, digest and command replay before append, with rollback on failures. All five have mock-JDBC/DDL tests, **not live DB tests**. No Spring controller, OIDC grant issuer, clinical approval or registration write path is wired.

## Security review and unresolved gates

1. The new SQL migration is a draft: it must not be deployed before DBA review, explicit non-owner role provisioning, restrictive/allow RLS policy design, table privileges, audit custody, retention and real negative cross-tenant tests.
2. The package-private JDBC adapter does not commit; transaction lifecycle, serialization failures, idempotency uniqueness, concurrent races, read-after-write visibility and rollback must be tested on a real PostgreSQL service with synthetic data.
3. The runtime must independently authenticate the reviewer, enforce MFA, facility/shift grants, segregation of duties and candidate scope. The current adapter is **not** a production authorization boundary.
4. The existing registration production boundary remains disabled. Reviewer evidence, even if persisted, must not authorize a clinical write without qualified human approval and verified production gates.
5. The Next.js More panel is a source-level change plus static-browser specimen; dependency installation, Next build, screen reader and native device testing remain open.
6. No clinical screen is complete or accepted. The 40-module/261-screen canonical inventory and release-denial policy remain unchanged.

## Evidence

See `evidence/batches/P01-P02-P05-v250-ten-batches.json`, `evidence/batches/v250-regression-core.json`, `evidence/batches/v250-regression-browser.json`, `registers/blockers.json`, and `evidence/v250-changed-files.json`. All test evidence uses synthetic values only.
