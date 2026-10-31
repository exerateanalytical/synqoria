# SYNQORIA — v0.11.0 implementation / integration review

## Delivered (10 independently checked source increments)

- P01-B0024: `PatientReadPort`, `JdbcPatientReadPort` and `ClinicalHttpGateway` port integration; a patient is not returned when the transaction or audit fails.
- P01-B0025: one generated correlation ID per HTTP request, propagated to the JDBC audit insert and visible in the HTTP response header. Tested over localhost HTTP with a JDBC proxy.
- P01-B0026: `IntegrationReadiness` probes identity/database/audit boundaries. Default is 503 and exceptions fail closed. **A test-supplied true probe is not production health certification.**
- P01-B0027: typed/allowlisted patient and active-facility JSON payloads; no freeform database fields are serialized.
- P02-B0024: volatile patient/facility scoped result store; mismatched patient, facility, epoch and unverified access cannot read cached record.
- P02-B0025: async patient read pipeline invalidates previous requests; late old-context responses cannot overwrite the current patient.
- P02-B0026: EN/FR four-tab mobile presentation; existing staff/patient Expo shell source imports labels and reasons, but protected tab navigation remains disabled pending verified server/native adapters.
- P03-B0015: production admission configuration requires identity verifier, authoritative grants and current revocation fence; cryptographic OIDC verification is **not supplied**.
- P03-B0016: second patient-scope fence between committed read and response serialization.
- P04-B0011: JDBC facility lookup includes tenant, cell and facility active joins, and rejects mismatches, duplicate rows and absent results. Scope tested with JDBC proxy, not live RLS.

## Test evidence and limitations

`evidence/batches/P01-P04-v110-checks.json` captures 26 check groups, including 59 TypeScript/Node tests, the existing Java tests, 7 new Java batch-specific tests (32 assertions), source checks of two Expo shells, OpenAPI/static SQL and Chromium **static specimens**. No actual Next.js app, Expo app, real PostgreSQL, OIDC integration, deployment, real-device accessibility or hospital-specific clinical acceptance has been achieved. npm ping failed DNS EAI_AGAIN. No hospital or clinical data is included in this package.

## Important implementation boundaries

- `ClinicalHttpGateway` legacy constructor accepts a separate in-memory/test `AuditedPatientAccess`; production must instead supply `JdbcPatientReadPort` and `JdbcFacilityAccessPort` plus verified identity and live dependency checks. This is a source architecture seam, not an approved production configuration.
- A real data source must use independently authenticated and restricted PostgreSQL roles with tested RLS, and an approved clinical access evidence policy. The mock JDBC fixtures in unit tests are NOT authorization evidence.
- A supported cryptographic JWT/OIDC verifier, approved issuer/audience/JWKS rotation, session revocation and privileges implementation are mandatory. Merely injecting a callback does not constitute cryptographic verification.
- Release and all 261 canonical clinical screen statuses remain **NOT CERTIFIED / NOT IMPLEMENTED**.

## Next steps

1. P01-B0028: provision supported database driver, isolated Postgres test container, migrations and restricted roles; run tenant-scope and rollback tests against actual PostgreSQL.
2. P03-B0017: implement verified OIDC/JWKS through maintained provider SDK, test revoked/expired/key-rotation cases end to end.
3. P02-B0027: compile Next.js/Expo, connect patient-switch UI to API with revocation/clear-cache states, run EN/FR screenshots at target widths and native device testing.
4. P04-B0012: add durable facility revisions and a service-owned authorized scope policy under tested DB constraints.
