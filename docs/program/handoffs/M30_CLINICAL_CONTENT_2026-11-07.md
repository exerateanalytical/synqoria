# M30 Terminology, Formularies and Clinical Content Governance — engineering handoff (2026-11-07)

Branch `work/claude-m30-clinical-content-2026-11-07`, from base `e14f589` (`work/seven-operations-real-workflows-2026-11-06`).
Scope: M30 only. **Not clinical approval of any content; no content is approved or active.**

## What exists
| Area | Path |
|---|---|
| Service (Java 25, Spring Boot 4.1.2, JDBC) | `services/clinical-content-core` (port 8096, role `synqoria_content_runtime`) |
| Migration | `services/clinical-content-core/db/migration/V001__m30_clinical_content.sql` — 14 tables, schema `synqoria_content` |
| OpenAPI 3.1 | `contracts/openapi/synqoria-m30-content-v1.yaml` — 30 operations, 16 permissions |
| Screens | `/workforce/content/{terminology,formulary,packages,approvals,activation}` (+ hub), EN/FR |
| Browser proxy | `apps/web/app/api/content/proxy/[...segments]/route.ts` (reuses the operations hospital sign-in session; no IAM change) |
| Tests | `services/clinical-content-core/src/test`, `tests/test_m30_openapi_contract.py`, `tests/m30_content_frontend.test.mjs` |
| CI | `.github/workflows/m30-content-ci.yml` |

Tables: `code_systems, code_system_versions, concepts, concept_synonyms, concept_mappings, formulary_entries, rule_packages, golden_cases,
validation_runs, approvals, activations, command_idempotency, audit_events, outbox`.

## Work packages
* **B01 architecture** — versioned registry, multilingual labels, synonyms, provenance, effective dates, licensing class (restricted systems require a licence reference, enforced by a CHECK).
* **B02 import** — canonical SHA-256 checksum, all-problems validation (duplicates, dangling parents, cycles, statuses, silent removals); rejected attempts are recorded and publish nothing; published labels are permanent.
* **B03 lookup** — effective-version search by code / English / French / synonym, hierarchy (roots, children, ancestors), inactive hidden by default, approved mappings only.
* **B04 formulary** — product/form/route/permitted-use/restrictions, maker-checker, atomic supersession, one approved version per key. Never writes orders or dispensing.
* **B05 rule packages** — declarative three-valued rules (missing facts => INDETERMINATE), immutable once validated (DB trigger), hash-bound.
* **B06 validation** — schema, golden cases, terminology integrity, dependency availability, regression against the previous version; stored reports.
* **B07 approval** — separate CLINICAL and POLICY sign-offs from two different non-author reviewers, expiry, revocation (also enforced by a DB trigger on APPROVED).
* **B08 activation** — atomic, exclusive, advisory-locked; dry-run impact; rollback re-pins the previous version; revocation suspends live content (fail-safe).
* **B09 screens** — 5 workstations; every control is wired or disabled with a visible reason (static test).
* **B10 verification** — see below.

## Verified locally (real runs)
* `mvn test` (Temurin 25.0.5): **27 tests, 0 failures** — 7 domain/admission + 20 against real `postgres:18.6` (Docker).
* `python tests/test_m30_openapi_contract.py`: 30 operations / 16 permissions in parity with the Spring routes.
* `node --test tests/m30_content_frontend.test.mjs`: 9/9 (EN/FR parity, error and validation code coverage, proxy allow-list, CSV and client checksum equal to the server algorithm, UX rules).
* `tsc` (TypeScript 5.8.3, Next 15.5.6 types): no errors in M30 files.
* Defects found by the tests and fixed: PostgreSQL regex repetition limit (255), unprivileged `FOR SHARE`, concurrent activation race (now advisory-locked), missing DB-level approval/transition invariant on packages.

## NOT verified / honest gaps
* GitHub Actions results for the exact pushed head are recorded on the PR after the run, not in this file.
* The Java 27 lane is informational (`continue-on-error`). Next 16.4.5 / React 19.3.1 were not built (dependency ownership of PRs #23/#24); no `next build`, rendered, responsive or screen-reader check.
* No load test. Search uses `lower()` prefix/ILIKE with `text_pattern_ops`; very large licensed vocabularies need a trigram/full-text index plan.
* Import payload is bounded (50k concepts, one JSON request); full national/international vocabularies need a streaming/staged loader.
* No dependency vulnerability scan or SBOM.

## Remaining external integration / production acceptance blockers
1. The clinical governance board must define who holds `m30:review_clinical`, `review_policy`, `approval_revoke` (P03 is the grant authority; M30 trusts the short-lived JWT claim).
2. Licensing review before any restricted terminology is loaded; none is bundled.
3. The clinical safety runtime that *consumes* active packages does not exist; M30 only publishes advisory rules and activation state. The outbox relay and consumers are not implemented.
4. Fact naming and severity vocabulary need clinical review; golden cases here are synthetic.
5. Real OIDC issuer/audience, DBA-provisioned runtime role, backup/PITR rehearsal, penetration test, accessibility audit.

## Integration instructions for other workstreams
* Read active content only via `GET /api/v1/content/activations/active` (scope from your token). Never read M30 tables directly.
* Proposed register values (registers are owned elsewhere and were not edited): `M30-S01..S05 code_status: implemented_unreviewed, review: pending`.
