# SYNQORIA v0.14.0 — Patient identity review and locked intake boundary

## 10 individually testable implementation increments

| Batch | Implementation | Scope of verification |
|---|---|---|
| P05-B0011 | Immutable HMAC-linked review evidence descriptor binds tenant, facility, distinct reviewer/registrar, candidate, command/patient identifiers and revision. | java_unit; **no production release** |
| P05-B0012 | HMAC-SHA256 proof issuer and constant-time verifier with named key, ten-minute maximum lifespan, tamper/expiry/key-rotation tests; trusted reviewer IAM and custody unavailable. | java_unit; **no production release** |
| P05-B0013 | Parameter-bound scoped JDBC reviewer-decision query requiring committed nonrevoked row and matching patient/command/fingerprint/MAC; quarantined FORCE RLS SQL table without grants. | jdbc_proxy_and_sql_static; **no production release** |
| P05-B0014 | Internal review admission requires authoritative stored decision, correct candidate revision, distinct reviewer, tenant/facility, server identity, shift and expiring signed proof. | java_unit; **no production release** |
| P05-B0015 | PII-free registration outcome envelope preserves unknown commit status and same-command retry semantics rather than guessing success. | java_unit; **no production release** |
| P02-B0033 | Semantically labelled bilingual intake-field structure with optional names, explicit birth-state options and no fabricated demographics. | ts_node_unit; **no production release** |
| P02-B0034 | Stable M01-S02 canonical intake navigation nested under patients, not an unauthorized top-level sidebar item. | ts_node_unit; **no production release** |
| P02-B0035 | Explicit local registration submission lock even with all simulated readiness booleans true; no clinical release short circuit. | ts_node_unit; **no production release** |
| P02-B0036 | Native bilingual screen-reader copy and 48px action model; separate read-only staff mobile preview is mounted while tabs remain disabled. | ts_node_and_source_static; **no production release** |
| P01-B0030 | Unexposed OpenAPI registration release-gate schema with all five gates fixed false; static SQL/API checks and no mutation endpoint. | contract_and_static; **no production release** |

## Critical findings and release blockers

1. ReviewDecisionAuthority issues proof from **server-provided identifiers** and a key held only in a test fixture here. It is not a real verified reviewer, independent approval, durable evidence recorder or production KMS. Never call `issue()` from a client endpoint or after a client-provided reviewer flag. Clinically sensitive writes remain disabled.
2. ReviewDecisionRepository SQL describes a restricted-role, already committed and nonrevoked reviewer row. Migration `V007__registration_review_quarantine.sql` has **FORCE RLS with zero permissive policies** and has not been run in PostgreSQL. No PostgreSQL role, JDBC driver, installation, migration or effective RLS test is verified.
3. ReviewAdmission checks proof, current candidate revision, independent reviewer, context and a committed-row flag. A server-specific orchestrator and real IAM are needed before any authorization is actionable. A caller-provided boolean is never trusted as proof by itself.
4. All app routes remain source-only, offline and **read-only**. No Next.js production build or Expo Android/iOS build has been verified; web and mobile do not accept PHI, create patients, or send a registration mutation.
5. The static JSX/React Native checks are not screen-reader/device acceptance. The visual CSS and semantic descriptors require runtime layout validation (320–1920 px; 200% text; EN/FR) in built applications.
6. **All 261 canonical clinical screens remain not_implemented** in the authoritative register. This source does not count as an operational M01-S02 screen. No HIPAA/GDPR/Cameroon legal or clinical accreditation assertion is made.

## Next engineering batch

**P05-B0016:** implement reviewer identity verification and durable event ownership tied to an actual restricted PostgreSQL role, then run cross-tenant/cross-facility RLS and transaction tests. Parallel unblocked task: move locked registration UI into a compiled Next/Expo environment when dependency registry access resumes.
