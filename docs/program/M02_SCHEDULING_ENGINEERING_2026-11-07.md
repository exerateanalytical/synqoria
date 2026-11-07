# SYNQORIA M02 — Source engineering handoff (2026-11-07)

Owner: ChatGPT, branch `work/chatgpt-m02-appointments-2026-11-07`, [draft PR #43](https://github.com/exerateanalytical/synqoria/pull/43).

**Commit at API contract addition:** stacked on PR #36; no merge to main.
**Implementation:** services/scheduling-core (Java25 LTS, Spring Boot 4.1.2, JDBC, OIDC JWT issuer/audience),
PostgreSQL 18.6 (V001–V002; 8 FORCE RLS relations), browser CSRF/origin
allowlist, five or six bilingual responsive screen workstations at `/workforce/scheduling/Mxx-Sxx`.
**API contract:** `contracts/openapi/synqoria-m02-scheduling-v1.yaml`.

## Business/technical authority notes
- **Business state:** appointments REQUESTED → CONFIRMED → ARRIVED → FULFILLED, plus cancellations and NO_SHOW; slot rescheduling uses one atomic unique constraint.
- **Capacity:** unique active reservation per slot, PostgreSQL GiST exclusion prevents overlapping slots for the same resource; 8 FORCE RLS tables.
- **No patient identity minting:** independently issued patient proof required, non-writable by runtime, revocable and time-of-use gated.
- **Remaining:** real MPI and scheduling policies, staff scheduling, client-specific timezone/DST rules, institution-approved appointment notifications, actual facility deployment and production acceptance.

## Engineering evidence
- Dedicated workflow verifies Java25/27 plus PostgreSQL18.6 with synthetic authorities.
- Dedicated web workflow verifies all canonical routes, Next16.4.5/React19.3.1 in an isolated CI-only PR23/24 dependency overlay. The root lockfile is never rewritten by this branch.
- All actual clinical/legal, identity and medical evidence integrations and hospital acceptance **DENIED until institutionally supplied and verified**.
- No claim of production medical or API interoperability certification. Root engineering CI still fails separately due to PR23/24-owned dependency integration.
- Separate Claude M24/M28/M30/M38/M39/M40 assignments and ChatGPT M27/M32/M34 PRs are not modified.
