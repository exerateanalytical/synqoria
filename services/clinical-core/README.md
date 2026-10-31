# services/clinical-core

Planned deployment ownership boundary. **No app/service code implemented or certified in baseline v0.1.0.** Continue under `docs/program/PHASES.md` with a real, tested build.

## P01-B0003 / P03-B0003 / P04-B0001 foundations

`db/migration/V001__hospital_scope_foundation.sql` is **unapplied draft PostgreSQL DDL**. It models cell/tenant/facility/department/location scoping with composite foreign keys and enables forced, policyless RLS (default deny). No application access may be granted until an independently tested policy/identity binding is designed; untrusted session variables or API headers MUST NOT grant access. The generated SQL has only static source assertions, not PostgreSQL execution proof. An actual migration must be trialled with a migration-role and a restricted, non-owner application role.

`PatientReadService` is an HTTP-independent, read-only port orchestration with indistinguishable NOT_DISCLOSED results for unauthorized and absent patients. It relies on server-provided `TrustedAccessContext` and `TrustedClinicalAccessEvidence`: current test fixtures are not authentication, consent-law decisions, real source-of-truth records or audited OIDC. Underlying repository/policy errors return SERVICE_UNAVAILABLE with no internal details. Integrators must not construct these evidence types from request bodies, raw JWT payloads, headers or other client claims. The facility directory is a versioned immutable **in-memory snapshot**, not a durable membership authority.
