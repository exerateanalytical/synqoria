# SYNQORIA legacy recovery merge policy (2026-10-31)

Main source of authority: published v0.57.0; the v0.27.1 recovery branch is older and divergent. This is a non-destructive historical reconciliation, not an assertion that newer runtime integrations have been tested or that clinical gates pass.

- Nine unique Java classes have been preserved in `org.synqoria.scheduling` and the isolated recovery test has been adjusted to the modern `AppointmentWindow.fromLocal` signature. They are **not** connected to booking write routes or certified.
- `ScopedAppointmentJdbcRead` is **legacy quarantine only**, references `scheduling_appointments` (which does not exist in v0.57.0 active migration scheme), and must not be called. Retained for provenance; reconcile before production use.
- The recovered `V012__appointment_read_quarantine.sql` conflicts in migration version with active `V012__appointment_reservation_quarantine.sql`; it is held under `docs/recovery/v0271/`, **not** in runnable migrations. The archival SQL must not be executed as an active migration.
- Historical recovery scripts, evidence, SQL source and status are kept in `docs/recovery/v0271/` unchanged for provenance; they are not part of the active build or release checks.
- Future changes require verification of appointment isolation, trusted identity, current database schema, RLS, outbox, lock semantics, clinical review and production build.

Source recovery provenance: v0.27.1 ZIP verified locally, and a 15-file unique-source recovery supplement compared with published v0.57.0; latest GitHub tree preserved.
