# SYNQORIA v0.28.0 — engineering, scheduling and security review

Date: 30 October 2026. **Status: engineering source and unit tests only; NOT a clinical release.**

## Verified continuity

Authoritative Library checkpoint: `/SYNQORIA/SYNQORIA_v0.27.0_cumulative.zip`. Retrieved and materialized alongside its SHA-256 file. External SHA-256 matched `90f707182746f7c208a32678b37ef75506f0b4c9ad2568c6f573c55ab0347705`; all 1,020 internal file hashes matched and no unlisted files were present. The original architecture v2.1 and UIUX v1.1 remain present. No prior incomplete work was promoted.

## Ten distinct implementation batches

1. **P01-B0043:** Real-file build-artifact attestation code; synthetic fixture tests cover successful hashing, missing output, missing Next marker, symlink and failed build. The fixture does **not** prove real Next/Expo builds.
2. **P01-B0044:** Manual `workflow_dispatch` npm lockfile candidate generation, trusted registry and integrity audit, artifact upload for review only; existing build CI no longer fails prematurely at setup-node cache. No automatic adoption or repository push.
3. **P02-B0093:** `attachMobileMoreDisclosure` controller integrated into actual Next source; compiled TypeScript executed in Chromium DOM with keyboard Tab/Shift+Tab loop, Escape focus restoration, outside click, resize and cleanup.
4. **P02-B0094:** Responsive short-viewport/safe-area CSS; static Chromium specimen tested 9 widths, French labels, 200% text and 460px viewport. Not a compiled Next or device screenshot.
5. **P06-B0001:** DST-gap and ambiguous-time rejection, explicit IANA zone and half-open overlap contract.
6. **P06-B0002:** Requested/confirmed/arrived/fulfilled and cancellation/no-show transitions, revision CAS and reason guard.
7. **P06-B0003:** Synchronized in-memory tenant/facility/resource reservation reference with concurrent conflict tests; quarantined PostgreSQL exclusion/RLS DDL static audit. No live PostgreSQL.
8. **P06-B0004:** Verified server-context patient/tenant/facility/capability access policy, including negative cases.
9. **P06-B0005:** Length-prefixed SHA-256 idempotency command fingerprint; deterministic and scoped.
10. **P06-B0006:** Nonmutating authorization/revision/conflict workflow preflight and outbox intent. **No database transaction, event emission or patient write.**

Each batch's exact command, exit code and log are in `evidence/batches/P01-P02-P06-v280-ten-batches.json`.

## Executed verification and interpretation

- **10/10** individual batches passed; Java compilation succeeded, six Java scheduling suites yielded **55 assertions**.
- **109/109** inherited Node UX-core tests passed; TypeScript UX-core compilation and source checks passed.
- **32** Chromium static CSS assertions passed across 9 widths; compiled TypeScript disclosure controller also passed its actual Chromium DOM test. Browser results are not compiled Next.js or native device evidence.
- **13/13** regression commands matched expected outcomes: **11 exited 0**, while dependency-lock gate and actual app-build runner **correctly exited 4 BLOCKED**. Full inherited Java security regression passed.
- `tests/test_v280_scheduling_ddl.py` and SQL migration auditor passed **static** constraints. No PostgreSQL parser/role/RLS execution.
- Clinical release remains **DENIED**; registration writes disabled; no clinical screens marked implemented or accepted.

## Safety review

**Positive:** Appointment transitions require optimistic revision, cancellation/no-show reason, explicit DST handling, patient-scoped trusted authorization, resource conflict checks and deterministic event identity. The SQL draft includes GiST exclusion and FORCE RLS but no app grants. The preflight deliberately cannot persist, publish or confirm a booking.

**Unresolved:** An in-memory reservation cannot prevent cross-node double-booking. `AppointmentWorkflowPreflight` is an advisory read, not a transaction; between preflight and commit another reservation could win. Production requires approved PostgreSQL exclusion constraint, row locks, non-owner RLS, atomic idempotency, audit and outbox in a real transaction. No scheduling clinical/operational release gate has passed.

**Builds:** npm registry lookup returned `EAI_AGAIN`; no reviewed root `package-lock.json`, no installed Next/Expo dependencies. The manual CI workflow has not been run; no Next/Expo compilation or device tests. CI source workflow does not claim such evidence.

**Human gates:** independent security review, qualified clinical review, privacy/legal signoff and supervised pilot are missing. No certification or phase advancement.

## Exact next unblocked work

**P01-B0045:** Run the manual lock-candidate workflow in trusted network-enabled CI, independently review the generated lockfile, validate package integrity and peer compatibility, then execute `npm ci`, actual Next.js build and staff/patient Expo exports with output attestation. This remains blocked locally until network/dependency prerequisites exist. Independently safe **P06-B0007**: design and unit-test a transactional appointment persistence port without enabling writes. **P05-B0027** remains blocked until real PostgreSQL restricted-role/RLS transaction evidence.
