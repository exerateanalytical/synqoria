# SYNQORIA v0.57.0 + v0.27.1 non-destructive recovery consolidation

Date: 2026-10-31. Target: `exerateanalytical/synqoria` `main`. This checkpoint merges **source unique to a divergent, older recovery branch** into the v0.57.0 code tree without replacing any newer application source. It does not claim that every historic batch was independently verified or that production release gates have passed.

- GitHub previously contained v0.57.0 (1,801 tracked source files), plus a subsequent GitHub audit document. No reset, force update or deletion is authorized.
- Original v0.27.1 recovery supplement had 15 unique project paths plus a SHA256 provenance record. All paths retained, but conflicting SQL and historical tooling were relocated to `docs/recovery/v0271/` to avoid activating outdated migrations or duplicate scripts.
- Nine distinct scheduling Java source files and the adapted recovery test are imported. The test was adjusted to the established `AppointmentWindow.fromLocal(local, zone, offset, duration)` signature, with deterministic behavior unchanged. Ten focused batch test cases pass, 52 assertions total.
- `ScopedAppointmentJdbcRead.java` is **legacy/quarantined**: it references old `scheduling_appointments`, and is not connected to runtime. It MUST NOT be enabled until reviewed against the v0.57.0 active table and RLS constraints.
- The recovered `V012__appointment_read_quarantine.sql` is archived under `docs/recovery/v0271/`. The active v0.57.0 `V012__appointment_reservation_quarantine.sql` is authoritative. **Do not execute the historical DDL** in a production or development migration runner.
- `tools/check_java_security.sh` is extended to execute the ten recovery safety tests. No booking writes were enabled.
- Inherited unit tests for TypeScript, canonical planning and application source passed. Live PostgreSQL non-owner RLS, production IdP/OIDC, Next.js/Expo application builds, device testing, clinical approval and rollout remain unverified.

Authoritative inventory remains 40 modules, 261 screen IDs, 0 clinically accepted. Clinical release remains denied.
