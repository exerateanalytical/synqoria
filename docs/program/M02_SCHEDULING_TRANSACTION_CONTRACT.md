# M02 Scheduling — pre-release implementation contract (v0.28.0)

Authority: `docs/authority/SYNQORIA_Platform_Architecture_v2.1.md`, M02 lifecycle, and `docs/authority/SYNQORIA_UIUX_Master_v1.1.md`, M02-S01–S05 and W02.

## Implemented, tested locally (not production)

- `AppointmentWindow`: explicit IANA time zone, daylight-saving ambiguity rejection, half-open intervals, technical 24-hour upper bound (not a clinical policy).
- `AppointmentAggregate`: REQUESTED → CONFIRMED → ARRIVED → FULFILLED; CANCELLED and NO_SHOW branches, revision compare-and-swap, cancellation/no-show reason.
- `ResourceReservationIndex`: synchronized in-memory overlap reference, tenant/facility/resource isolation, concurrency unit tests. **Not authoritative in a multi-process deployment.**
- `AppointmentAccessPolicy`: requires backend-trusted tenant/facility/capability AND patient-scope; never derives scope from headers.
- `AppointmentCommandFingerprint`: deterministic, length-prefixed command digest, never log patient identifiers in test evidence.
- `AppointmentWorkflowPreflight`: denied-safe authorization, revision and conflict advisory; creates an outbox **intent**, not an emitted event.
- `V012__appointment_reservation_quarantine.sql`: proposed PostgreSQL exclusion constraint and FORCE RLS. No app grants or policies; migration NOT executed against PostgreSQL.

## Mandatory authoritative transaction before activation

1. Verify trusted OIDC/MFA, tenant, facility, actor, patient scope, purpose, role and shift on the server. Never accept user-supplied tenant headers as authority.
2. Begin a real database transaction with a restricted, non-owner, non-BYPASSRLS role. Apply reviewed tenant/facility/patient RLS and confirm row-level isolation using real PostgreSQL tests.
3. Lock appointment and resource records, verify expected revision and command idempotency in the same transaction; prevent duplicate or overlapping confirmed reservations using the exclusion constraint.
4. Update appointment state, append audit, record durable outbox event and idempotency outcome **atomically**; rollback all on any failure. Never publish from the preflight stage.
5. Re-read authoritative state for replay and reconciliation. No clinical/patient existence disclosure on denied or mismatched scope.
6. Test concurrent double-booking, DST gaps/overlaps, cancellation/rebooking, stale revision, cross-tenant/patient denial, failover and outbox delivery with live PostgreSQL.

## Screens and release status

M02-S01–M02-S05 remain **not implemented, not integration-tested, not clinically reviewed, not accepted**. P06 is foundational work only, not phase-certified. Appointment creation/confirmation and patient-facing booking remain **disabled** until independent security and clinical approval.

## v0.41.0 implementation delta (P06-B0044–B0063)

- The quarantined JDBC adapter now uses the actual `synqoria_appointment_reservations_quarantine` table and `lifecycle` column, with tenant/facility/patient/revision AND current-state compare-and-swap. A previous unqualified `clinical.appointments` reference was corrected.
- A scoped `SELECT ... FOR UPDATE` snapshot, `V014` idempotency quarantine table, command replay check, audit chain append, outbox append and same-transaction four-record consistency proof are implemented as Java source and tested against mocked JDBC. No production grant/policy exists.
- Server-trusted clinical access evidence, MFA/shift/purpose/legal basis, per-table FORCE RLS/non-owner proof, server-keyed actor pseudonyms, SQL timeouts and fresh-connection bounded retries are wired into the **internal adapter only**. The key must be supplied by a trusted KMS-backed runtime; none is configured.
- Domain/SQL lifecycle parity was checked across all 36 state pairs. `ARRIVED -> CANCELLED` was removed from the SQL transition policy to match the conservative domain rule. No clinical treatment decision is encoded.
- An authorized administrative status-read adapter and scoped audit-chain readback exist; neither is exposed as an application endpoint. Readback alone cannot detect tail deletion without an independently anchored digest.
- Source-only `V012`–`V014` migrations remain unapplied. **No real PostgreSQL restricted-role/RLS, concurrent booking, outbox dispatch, OIDC, mobile, end-to-end or clinical sign-off evidence exists.** P06 is not certified.

## v0.42.0 replay consistency extension

`AppointmentReplayProof` requires the previously committed audit and outbox rows to exist before acknowledging an idempotent replay. Tested with mocked JDBC only; not an independently durable external audit anchor. The historical v0.41.0 B0044 test was retained and adapted to the stronger seven-parameter scoped SQL CAS.

## v0.43.0 parallel source reconciliation

A concurrent v0.42 source file used `appointment_reservations.status`, inconsistent with the quarantined V012 schema. Its original is archived under `docs/archive/v042_parallel/` and deliberately excluded from compilation. The active M02 adapter remains `AppointmentTransitionService` plus scoped CAS, audit/outbox/idempotency and replay proof; all live gates remain denied.
