# SYNQORIA — 261 canonical application route coverage

**Status date:** 2026-11-05  
**Work branch:** `work/canonical-261-route-coverage-2026-11-05`  
**Authority:** architecture v2.1; UI/UX v1.1; 261-screen canonical register.  
**Clinical release:** DENIED. This document is not clinical acceptance or certification.

## Scope and precise counting

- **261/261 canonical Next.js application paths** now resolve in `/workforce/clinical/[screenId]`; 41 navigation groups: 40 modules plus shared workspace.
- **10 existing application slices preserved** without replacement: five guarded backend-read subsets (facility, cell readiness, patient identity metadata, triage summary, bounded triage queue) and five functional nonclinical/nav surfaces (personal settings, help, safe retired routes, document library and print preview).
- **251 additional application routes** now render a screen-specific React workstation, using their actual registry ID, title, layout, data group descriptions, module siblings, navigation workflow dependencies, and associated sample document links. These are **domain-locked** rather than clinically functional because their authoritative API/field contracts and production integration are not complete.
- **0/261 clinically accepted**; **0/40 modules clinically certified**; no new clinical mutations, invoices, consent signatures, prescription issuance, laboratory release, prescribing or insurer remittances were enabled.
- **No browser mock data**; **no fabricated patient or financial records**; no browser PHI storage; no opportunistic POST/PUT/PATCH/DELETE; clinical commands are disabled.

## Seven distinct layout families

| Family | Count is authoritative in `canonical-screens.json` | Domain-locked behavior |
| --- | --- | --- |
| F | Form | Screen-specific required field groups, disabled entry, reviewed-dictionary warning |
| L | List | Source-specific column headings, verified empty state, no unrestricted search |
| D | Detail | Screen-specific descriptors, no inferred patient record |
| X | Review | Requested evidence/decision groups, independent sign-off explicitly denied |
| B | Board | Two-stage service/clinical integration barriers; no fabricated status totals |
| T | Timeline/analytics | Metric/data headings, protected empty results; export denied |
| H | Dashboard | Exact task/metric groups, no fake workload or KPI values |

Client-side module screen navigation/search, canonical workflow links, document-template sample links, accessible tabs and language selection are usable; those are **nonclinical** operations. Checking the current staff session calls the existing real `GET /api/clinical/facility` BFF with no-store and fails closed. A successful facility read is not proof that an unrelated domain supports data access or writes.

## Route and implementation registers

- `registers/screen-routes.json`: 261 URLs and one of `api_read_subset_not_clinically_complete`, `nonclinical_functional` or `route_registered_domain_adapter_missing` per canonical ID. Every clinical accepted/write flag remains false.
- `registers/operational-screen-progress-v0.1.json`: `routedScreenUrls=261`, `routeSlices=10`, `routeOnlyLocked=251`, `clinicallyAccepted=0`.
- `tests/canonical_261_route_registry.test.mjs`: exact ID, module, enum, and clinical release invariant tests.
- `tests/canonical_261_route_browser.mjs`: route HTTP coverage (261), responsive seven-layout sample set (320/768/1440), backend-denial verification, disabled mutations, 261-screen directory, no-glow and width checks.
- `.github/workflows/canonical-route-261.yml`: source, TS, production Next, browser checks, machine evidence.

## Next production engineering stage (NOT achieved)

Per module: adopt an independently reviewed field/terminology dictionary; implement typed domain DTOs, server-side OIDC/MFA and current grant revalidation, hospital-cell and patient/encounter scope, transactional PostgreSQL persistence, state transitions and conflict handling, audit outbox, offline safety policy, EN/FR, disability support and real integration tests. Reconcile P06/P07 branches before using clinical write endpoints. Follow the owner-specified **30 verified batches per run** when substantive implementation is requested, but never count routing aliases, tests not run or unsupported clinical approvals as batches.

**Warning:** This is a real application route layer, not 261 production-complete screens. Navigable URL count, nonclinical UI interactions, integrated clinical reads, validated clinical commands, certified screens and certified modules are separately tracked.
