# SYNQORIA — one-cycle recovery plan for 240 missing operational screen integrations

**Date:** 5 November 2026  
**Plan authority:** SYNQORIA architecture v2.1 and UI/UX v1.1; canonical 261-screen register  
**Repository:** `exerateanalytical/synqoria`  
**Starting code:** `work/m04-m06-read-operations-consolidation-2026-11-05` at `31ddc403d92046ab94326e649c150c94ecc26a63` (draft PR #20).  
**Binding manifest:** `registers/operational-240-one-cycle-batches-v1.json` — exact IDs, required fields, actions, assignment and acceptance evidence for **30 packages × 8 screens = 240 unique screens**.  
**Status:** **PLAN ONLY; no screens implemented by this document; clinical certification DENIED.**

## Decision and count accounting

- 40 planned modules plus 12 cross-cutting shared screens = **261 canonical application screens**.
- **240** currently have a Next.js URL and a domain-locked shell but **no completed authoritative domain integration**. Of those 240, seven are shared-workspace screens; the other 233 belong to clinical or operational modules.
- **21** already have *partial* authorized-read or nonclinical function, **not finished clinical workflows**. These 21 need a separate completeness audit before certification.
- Zero clinical workflows accepted, zero modules certified. Templates/form prototypes and static route counts do not satisfy operational acceptance.
- A **single execution cycle** means one coordinated build programme with up to **30 independently verifiable engineering packages**, not one giant unreviewed commit or 240 fake completion labels. Foundational dependencies gate dependent packages; blocked packages must be recorded accurately and independent packages may continue.
- No engineering team, AI agent, database, production OIDC identity issuer or clinical approval may be presumed to exist. **Do not claim an end-to-end one-run completion unless every condition and test below actually passes.**

## Four parallel domain ownership lanes

| Lane | Missing screens | Authority and integration scope |
| --- | ---: | --- |
| Clinical + patient workflows (SHARED, M01–M18) | **125** | Patient registration, booking, triage, encounter, notes, observations, prescribing, drug administration, diagnostics, surgery/transfusion/referral, specialties and patient portal |
| Finance + supply (M19, M20, M21, M22, M24) | **30** | Real charges, invoice/receipt/ledger, claims, procurement, inventory, staff payroll and approvals |
| Hospital operations (M23, M25–M29, M37) | **32** | Biomedical assets, transport, meals, mortuary custody, analytics/reporting, facility configuration, notifications |
| Platform + security + integration (M30–M36, M38–M40) | **53** | Terminology, forms/docs, consent, IAM, audit, interoperability, devices, offline sync, cell lifecycle, backup and recovery |
| **Total** | **240** | One canonical owner per screen; cross-domain workflows use versioned APIs/events instead of duplicated application logic |

## Non-negotiable gates before code batches

**G0 — Reconcile before branching:** Compare main and both PR stacks (#11–#20 for web/UI/document code; #1–#10 for P06/P07 triage and clinical backend). Preserve both histories. Produce a branch/commit manifest and OpenAPI/DB migration conflict report, consolidate in an integration baseline, and prohibit overwrite of newer parallel code. PR #20 is **draft, unmerged**.

**G1 — Trust and isolation:** Implement a verified production-grade OIDC/OAuth2/MFA login issuer, short-lived/revocable sessions, current grants, per-hospital-cell isolation, server-side tenant/facility/actor/patient/encounter context, RLS, consent and emergency-access policies. Unknown permissions must deny before source queries. The existing sealed cookie format is not a production identity issuer.

**G2 — Canonical domain contract baseline:** For each module, reconcile existing Spring/Java and P07 read/write contracts, define typed OpenAPI commands and queries, exact field dictionaries (including requiredness, units, code systems, error states, scopes, and EN/FR), data ownership and versioned event contracts. No generic CRUD endpoint may replace a clinical state machine.

**G3 — Persistence and provenance:** Production-like PostgreSQL test environment with migrations, RLS/scoping, backup/restore validation, audited transactional writes, optimistic revisions, idempotency keys, event outbox/inbox, encryption, expiry/retention rules and failure recovery. Finance additionally requires balanced-ledger invariants and reversals. No untested mock data counts as real integration.

**G4 — Per-screen complete implementation:** Every field, section, tab, filter, action, modal, state, validation, queue, role permission, printable document and export must connect to authoritative source/service. Each screen must have explicit empty/denied/conflict/stale/offline/retry paths and real mobile/desktop navigation. Screen codes must not be remapped or dropped.

**G5 — Final cross-module validation and qualification:** Full application path tests and representative domain-specific workflows, then independent security, privacy, concurrency, resilience, accessibility and clinical acceptance. Re-audit the 21 earlier partial screens; a 240-screen pass alone does not prove 261 production-ready.

## Exact 30-batch assignment (eight unique screens per package)

The following is the **complete 240-screen assignment**; the JSON manifest expands each ID into its actual screen title, layout, required data groups, intended primary action, domain lane, evidence requirements and planned-only status. Batches may be implemented in parallel **after gates**; the numbering is a stable ownership ID, not permission to violate dependencies.

| Batch | Module groups | Exact canonical screen IDs | Initial status |
| --- | --- | --- | --- |
| 01 | SHARED, M01 | G-S01, G-S02, G-S04, G-S05, G-S07, G-S08, G-S09, M01-S02 | Planned |
| 02 | M01, M02 | M01-S04, M01-S05, M01-S06, M02-S01, M02-S02, M02-S03, M02-S04, M02-S05 | Planned |
| 03 | M03, M04, M05 | M03-S02, M04-S01, M04-S03, M04-S04, M04-S05, M04-S06, M04-S07, M05-S02 | Planned |
| 04 | M05, M06, M07, M08 | M05-S03, M06-S01, M07-S01, M07-S02, M07-S03, M07-S04, M07-S05, M08-S01 | Planned |
| 05 | M08, M09 | M08-S02, M08-S03, M08-S04, M08-S05, M08-S06, M08-S07, M09-S01, M09-S02 | Planned |
| 06 | M09, M10 | M09-S03, M09-S04, M09-S05, M10-S01, M10-S02, M10-S03, M10-S04, M10-S05 | Planned |
| 07 | M11 | M11-S01, M11-S02, M11-S03, M11-S04, M11-S05, M11-S06, M11-S07, M11-S08 | Planned |
| 08 | M11, M12 | M11-S09, M12-S01, M12-S02, M12-S03, M12-S04, M12-S05, M12-S06, M12-S07 | Planned |
| 09 | M13, M14 | M13-S01, M13-S02, M13-S03, M13-S04, M13-S05, M13-S06, M13-S07, M14-S01 | Planned |
| 10 | M14, M15 | M14-S02, M14-S03, M14-S04, M14-S05, M14-S06, M15-S01, M15-S02, M15-S03 | Planned |
| 11 | M15, M16 | M15-S04, M15-S05, M15-S06, M16-S01, M16-S02, M16-S03, M16-S04, M16-S05 | Planned |
| 12 | M17 | M17-S01, M17-S02, M17-S03, M17-S04, M17-P01-S01, M17-P01-S02, M17-P01-S03, M17-P02-S01 | Planned |
| 13 | M17 | M17-P02-S02, M17-P02-S03, M17-P03-S01, M17-P03-S02, M17-P03-S03, M17-P04-S01, M17-P04-S02, M17-P04-S03 | Planned |
| 14 | M17 | M17-P05-S01, M17-P05-S02, M17-P05-S03, M17-P06-S01, M17-P06-S02, M17-P06-S03, M17-P07-S01, M17-P07-S02 | Planned |
| 15 | M17, M18 | M17-P07-S03, M17-P08-S01, M17-P08-S02, M17-P08-S03, M18-S01, M18-S02, M18-S03, M18-S04 | Planned |
| 16 | M18, M19 | M18-S05, M18-S06, M18-S07, M18-S08, M18-S09, M19-S01, M19-S02, M19-S03 | Planned |
| 17 | M19, M20 | M19-S04, M19-S05, M19-S06, M19-S07, M20-S01, M20-S02, M20-S03, M20-S04 | Planned |
| 18 | M20, M21 | M20-S05, M20-S06, M21-S01, M21-S02, M21-S03, M21-S04, M21-S05, M21-S06 | Planned |
| 19 | M22, M23 | M22-S01, M22-S02, M22-S03, M22-S04, M22-S05, M23-S01, M23-S02, M23-S03 | Planned |
| 20 | M23, M24 | M23-S04, M23-S05, M24-S01, M24-S02, M24-S03, M24-S04, M24-S05, M24-S06 | Planned |
| 21 | M25, M26 | M25-S01, M25-S02, M25-S03, M25-S04, M25-S05, M26-S01, M26-S02, M26-S03 | Planned |
| 22 | M26, M27, M28 | M26-S04, M27-S01, M27-S02, M27-S03, M27-S04, M27-S05, M28-S01, M28-S02 | Planned |
| 23 | M28, M29, M30 | M28-S03, M28-S04, M28-S05, M29-S01, M29-S02, M29-S03, M29-S04, M30-S01 | Planned |
| 24 | M30, M31 | M30-S02, M30-S03, M30-S04, M30-S05, M31-S02, M31-S04, M31-S05, M31-S06 | Planned |
| 25 | M32, M33 | M32-S01, M32-S02, M32-S03, M32-S04, M32-S05, M33-S01, M33-S02, M33-S03 | Planned |
| 26 | M33, M34 | M33-S04, M33-S05, M33-S06, M33-S07, M34-S01, M34-S02, M34-S03, M34-S04 | Planned |
| 27 | M34, M35, M36 | M34-S05, M35-S01, M35-S02, M35-S03, M35-S04, M35-S05, M35-S06, M36-S01 | Planned |
| 28 | M36, M37 | M36-S02, M36-S03, M36-S04, M36-S05, M37-S01, M37-S02, M37-S03, M37-S04 | Planned |
| 29 | M38, M39 | M38-S01, M38-S02, M38-S03, M38-S04, M38-S05, M39-S01, M39-S02, M39-S03 | Planned |
| 30 | M39, M40 | M39-S04, M39-S05, M40-S01, M40-S02, M40-S03, M40-S04, M40-S05, M40-S06 | Planned |

All 30 batch packages: **240 IDs, zero duplicates, zero omissions against the current domain-locked register.**

## Per-screen definition of done (14 separate evidence gates)

1. Complete reviewed field dictionary, mandatory fields, types, clinical units/codes, restrictions and EN/FR.
2. Typed, versioned OpenAPI read and write contracts with role- and consent-dependent errors.
3. Verified server-side hospital, tenant, facility, patient, encounter and current actor scoping; IDOR denied.
4. Real transactional DB persistence when the action changes state, not just client state or a disabled button.
5. Explicit legal workflow state machine, optimistic revision control, safe conflicts and reversals.
6. Clinical safety, maker/checker, e-signature and role approvals where required (not invented).
7. Provenance, audit trail and outbox events; idempotent command replays.
8. Every specified UI action/control, workflow/tab and navigation transition is executable or explicitly policy-denied.
9. No fake patient data, leaked PHI, local browser clinical storage, deceptive “done” statuses or unapproved automation.
10. Mobile 320/375/768, desktop 1024/1440/1920, WCAG 2.2 AA, readable font/icon/color, zero glowing effects, no cramped or clipped content.
11. EN/FR labels and localization, loading/empty/error/offline/recovery flows and user-safe validation.
12. Correct forms, receipts, clinical reports, templates, signatures, audit metadata and protected exports where the workflow requires them.
13. Successful unit, contract, DB integration, transactional/concurrency/security and Playwright end-to-end tests with persisted evidence.
14. An accountable clinical/operational owner accepts the complete screen against a named approved specification; otherwise retain `BLOCKED`.

The test suite must verify **deny-by-default, cross-tenant and cross-patient access, forged session/capabilities, expired consents, stale revisions, duplicate commands, partial write failures, bulk selection, retry, low bandwidth, long EN/FR strings, sensitive print output, protected exports and recovery**.

## Orchestrated one-cycle execution procedure

1. **Preflight/freeze (G0–G3):** Take authoritative SHA snapshots; diff all branches; run existing CI; enumerate missing APIs/migrations/roles; generate the 240-screen ownership and dependency graph; initialize *real* PostgreSQL/integration fixture environment. If a critical foundation is missing, mark dependent screens `BLOCKED_DEPENDENCY`, never `DONE`.
2. **Shared foundation implementation:** Complete session issuance, patient access fencing, RLS, durable audit/outbox, credential lifecycle, typed clinical/financial commands, and smoke tests. Nothing high-risk may bypass these prerequisites.
3. **Execute 30 packages:** Assign each package a unique branch/owner; verify eight exact screen IDs are in scope. Implement the full workflow for each screen; commit and push independently after each tested batch; publish a machine-readable screen acceptance register. Preserve checked-in evidence and SHA, with no two writers editing the same files unsynchronized.
4. **Cross-domain integration:** Test scenarios: registration→booking→triage→encounter→note/observation→lab/radiology→pharmacy→billing→discharge; surgery/transfusion consent; purchasing→stock→dispensing→charge; payer authorization→claim→remittance; staff shift→privilege→audit; offline resync conflict→reconciliation; backup→restore→safe reopening.
5. **UI completeness audit:** Compare every one of the 240 screens against its field/action contracts; inspect 320/375/768/1024/1440/1920 widths; keyboard/focus and EN/FR accessibility. Compare 376 structured forms + 304 PDF layout designs and 662 unique document codes to issuance workflows; sample PDFs are not issued documents.
6. **Validate/reconcile:** Revalidate the earlier 21 partial screens, full 261 URLs, all 40 modules, reports and printables. Generate completeness %, tests, missed IDs, status per screen, duplicate changes, unmerged PRs, migration status and evidence hash. Mark certification **DENIED** until independent clinical release prerequisites are actually met.

## Machine-readable acceptance register and stop conditions

For each screen, record `screenId`, `batchId`, `module`, `specVersion`, `apiContract`, `roleMatrix`, `migration`, `dataDictionary`, `readTest`, `writeTest`, `securityTest`, `browserTest`, `documentTests`, `reviewer`, `evidenceSha256`, `sourceCommit`, `status`. Allowed statuses: `PLANNED`, `BLOCKED_DEPENDENCY`, `IN_PROGRESS`, `IMPLEMENTED_UNVERIFIED`, `VERIFIED_PARTIAL`, `ACCEPTED`. *No batch counted completed merely because files, routes, UI prototypes, metadata or static tests exist.*

**Release fails closed if** any tenant/cell isolation is unproven, patient scope incorrect, required forms absent, clinical state/command safety unverified, financial ledger unbalanced, document signing forged, certificate overstated, or rollback/recovery cannot be demonstrated.

### Current verifiable result of this plan

- 30 exact eight-screen engineering work packages **planned**.
- 240 unique missing screen IDs assigned and requirements mapped.
- **0 of those 240 domain integrations completed by the planning exercise.**
- Existing draft PR #20 remains the real implementation baseline, and the dedicated CI pass from the earlier implementation is not acceptance of the 240 missing workflows.

**Execution feasibility:** Coordinating all 240 in one long-running, instrumented engineering execution is a *programme design*, not a credible guarantee for one conversational run. Production clinical identity and live DB/integration infrastructure, implementation code and evidence must be present before any completion claim. No arbitrary 30-batch target overrides actual validation.
