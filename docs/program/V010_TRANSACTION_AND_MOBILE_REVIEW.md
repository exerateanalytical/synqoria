# SYNQORIA engineering review — v0.10.0

## Authority and completed batch scope
Parent: verified `v0.9.0` checkpoint. This release records ten **foundation** increments, not a clinical application or a production release. Four engineering, three web/mobile shared-state, two authorization-safety and one hospital-directory increment were implemented and tested at their stated scope.

**Patient read with audit:** `TransactionalPatientReadJdbc` refuses to disclose without server-owned tenant, facility, actor and patient grants. A separate, externally approved clinical authorization predicate must also allow the request. A single caller-supplied writable JDBC transaction reads the authoritative row and inserts the corresponding audit intent. Neither patient data nor a success result is returned until the same transaction commits. Failures of auditing, commit, patient lookup, scope or authorization fail closed. Proxy-based JDBC tests check failure paths, bind parameters, and commit/rollback order but are **not PostgreSQL integration tests**.

**Critical remaining transactional risks:** A policy authorization proof and patient snapshot must be measured against the appropriate authoritative clinical safety revision, with the required isolation level and locking; current predicate only provides an integration seam, not an approved policy implementation. RLS is default deny, so the draft audit table intentionally has no working app role until a controlled DB authorization approach is reviewed. A well-scoped SQL WHERE clause alone never establishes DB authorization. Audit custody, tenant-independent audit access controls, owner/grant separation, append-only enforcement, retention and external anchoring are not tested or certified.

**Web/mobile client guard:** patient-response epochs invalidate late responses after facility/patient changes, offline actions cannot self-authorize clinical effects, and a provisioned cell endpoint cannot be injected from untrusted QR/header/query inputs. The compiled shared TypeScript package is **not a compiled Next.js/Expo application**. Native screen interactions remain intentionally disabled until a real verified backend session exists. No clinical PHI is stored by these new utilities.

**Security:** `SessionLifetimeFence` requires a current, unexpired grant with bounded revocation freshness, but it is not a cryptographic OIDC or JWKS verifier; `PatientReadReleaseFence` only exposes committed authorized results. Real OIDC verifier, enforcement at every controller, accurate revocation and session propagation, database integration, emergency policy and qualified clinical approval remain mandatory.

## Navigation and design
Preserve Warm Clinical palette and existing EN/FR, three web shells, separate mobile shells and accessible four-tab navigation. No new rendered screen is counted as complete. Critical medication/allergy fields require untruncated accessible text once their workflows are built. Previously verified *static Chromium specimens* do not certify Next.js/Expo; re-run live browser accessibility, keyboard, 200% font and nine widths on actual applications.

## What was and was not executed
`npm run check:ux` (TypeScript compile + Node tests), `tools/check_java_security.sh` (Java 21 compilation and assertions), SQL static safety check, and all inherited regression checks. npm registry lookup again failed `EAI_AGAIN` so dependencies, Next.js, Expo and native builds remain blocked. The PostgreSQL binary/server and JDBC driver were not available; database migrations, privilege configuration and RLS enforcement remain unexecuted. This checkpoint can be certified only as a tested source increment; not clinically or operationally certified.

## Next
Before adding further isolated helpers, prioritize network-enabled real Next.js/Expo builds; PostgreSQL/testcontainers integration of V001–V004, transaction isolation and default-deny RLS; actual OIDC/JWKS and revocation; HTTP read adapter use of the transactional audit repository; real patient-context UI invocation with session expiry. Keep release gating closed.
