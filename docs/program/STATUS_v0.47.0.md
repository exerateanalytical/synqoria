# SYNQORIA v0.47.0 — verified cumulative reconciliation

**Authoritative source at execution start:** v0.44.0 SHA256 `61efe14fa0f0f59d365f26a3b9e66ddca80b8cb5ebe3940bce8885e3116a1176`, 1,288 ZIP entries, CRC/path safety passed. The original 19-entry baseline SHA manifest was verified. v0.44 had no full up-to-date self-contained file SHA manifest.

**New engineering batches implemented and individually focused-tested in this execution:** **15 of 60 (25%)**, P06-B0070–B0084. **45/60 (75%) remain**; not counted as completed. **Additional independent reconciliation:** 2 migration-gate components, now P06-B0085–B0086, were cherry-picked from a parallel v0.46 source and independently retested. They are **not counted toward this run's 15 new batches**, preventing double counting.

## New implementation P06-B0070–B0084

1. B0070: Strict PostgreSQL transaction-local tenant/facility/actor/patient echo verification.
2. B0071: Bounded lock/statement/idle SQL timeout echo verification and canonical duration parsing.
3. B0072: Patient-scoped audit/outbox event integrity query with audit digest recomputation.
4. B0073: Bind same-transaction commit proof to event integrity and command digest.
5. B0074: Bind idempotent replay proof to event integrity.
6. B0075: Reject truncated audit history relative to authoritative appointment revision.
7. B0076: Verify each audit history row has matching outbox payload digest.
8. B0077: Require replay event proof to match the original scoped command digest.
9. B0078: Require committed idempotency row to match expected command digest.
10. B0079: Recompute previous audit row digest before appending.
11. B0080: Enforce SQL NULL revision semantics for pending/committed idempotency evidence.
12. B0081: Verify audit/outbox history before appointment state mutation.
13. B0082: Verify audit/outbox history before replay acknowledgment.
14. B0083: Repair stale lifecycle test to deny ARRIVED→CANCELLED.
15. B0084: Reconcile package/ledger/phase versions and historical register test.

## Independently reconciled parallel work (not newly counted)

- B0085: Flyway SQL migration inventory gate (4 Python tests passed).
- B0086: Immutable SHA-256 lock for all 14 existing migrations (6 Python tests passed; 14 hashes verified).

The parallel v0.46.0 ZIP's outer SHA/CRC matched, but its embedded manifest had **9 mismatches or missing files** and did not cover its newly added migration-gate source. That archive was **not accepted as verified cumulative authority**. Only the five named migration-gate files were independently adopted and tested. Its original IDs B0070/B0071 conflicted with this execution and were remapped to B0085/B0086. Details: `docs/program/V047_RECONCILIATION.md`.

## Executed verification

- Java compilation: **312 main/test source files**, exit 0.
- **15/15 batch-focused commands**, exit 0 (Java mock JDBC tests and register tests).
- **68/68 runnable scheduling Java tests** passed.
- Existing Java security regression: exit 0; 99 PASS output lines (not clinical certifications).
- TypeScript UX check: exit 0; **109/109 Node tests** passed.
- Web/mobile source static checks: exit 0; NOT native or Next.js production builds.
- Register integrity: exit 0; **40 canonical modules, 261 canonical screens, 20 phases**.
- Quarantined SQL source audit: exit 0; 5 mutation tests passed.
- Original seed manifest: 19/19 entries verified.
- Parallel migration inventory: 4/4 tests; immutable checksum gate: 6/6 tests; 14/14 migration SHA locks verified.

## Remaining blockers and release decision

**No live PostgreSQL/restricted-role RLS runtime, concurrent database booking, transactional rollback/idempotency runtime, production OIDC/MFA, Next.js production build, Expo Android/iOS binaries/device testing, clinical safety approval, independent security review, DR/pilot qualification or external regulatory acceptance.** SQL migrations remain quarantined, with no application grants or RLS policies approved. Do not enable clinical writes.

**Clinically accepted screens 0/261 (0%); certified modules 0/40 (0%). Overall implementation completion percentage remains unmeasured. P06 not certified. Clinical release DENIED.**

**Next batch: P06-B0087.** Prioritize a real synthetic PostgreSQL restricted-role integration environment if provisioned; otherwise continue independent, verified scheduling/triage foundations without bypassing release gates.
