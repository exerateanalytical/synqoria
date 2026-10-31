# v0.30.0 M02 safety and implementation review

- New preflight classes are intentionally **not** wired to HTTP, Spring, or PostgreSQL.
- The current in-memory resource index is advisory and not a concurrency authority. `conflictsExcludingSelf` prevents self-conflict on reschedule but does not prevent concurrent double-booking.
- Quarantine SQL enables and forces RLS and revokes PUBLIC; it creates no RLS policies or application grants, so non-owner application access is denied by default.
- Restricted JDBC role check uses mocked JDBC for unit tests only; it is **not** proof of production PostgreSQL role correctness.
- Sensitive cancellation and no-show reasons are bound into outbox digests without logging plaintext. The reason remains in the in-memory aggregate, not a protected persistence implementation.
- Scope checks use backend `TrustedAccessContext` and suppress patient-existence disclosure on denial. No production OIDC verifier or patient permission refresh is integrated.
- Static OpenAPI contract includes deliberately non-resolving identity discovery URL; never deploy it as an operational endpoint.
- Production appointment write, booking, attendance, cancellation, and read routes are disabled until clinical, security, transaction, privacy, and operational gates pass.
- No patient-specific medical protocols or scheduling priority rules are introduced.
