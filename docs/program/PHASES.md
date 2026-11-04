# SYNQORIA — Canonical Engineering Phases

**Canonical phase definitions:** 20 planned programme phases P00–P19. The overall phase-status register on `main` is not automatically current while parallel PRs remain unmerged. **Owner-approved execution cadence (2026-11-04):** 30 substantive and individually tested engineering batches within each requested execution, with immediate verified GitHub publication per batch; a run that cannot meet 30 is expressly **incomplete**, not a new 1/5/10-batch target. **Stage sequence:** complete engineering backlog → implement all 261 screens → integrate/complete all 40 modules → full audit and formal certification. See [Mandatory 30-batch execution policy](SYNQORIA_EXECUTION_POLICY_30_BATCHES_2026-11-04.md). These four delivery stages do not replace the twenty phase definitions. Essential security/clinical safety checks remain required during implementation. Multiple phases may contribute work to a 30-batch session after cross-workstream reconciliation.

## P00 — Recovery and authoritative inventory

Modules: cross-cutting.

Capture spec provenance; generate exact module/screen registers; audit repository availability; establish evidence ledger; clarify unresolved inputs without stopping safe work.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P01 — Engineering workspace and contract foundation

Modules: cross-cutting.

Monorepo boundaries; pinned toolchain; ADRs; OpenAPI/event schema conventions; database migrations; dev containers; CI lint/tests; per-hospital cell model.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P02 — Cross-platform design system and navigation

Modules: cross-cutting.

Tokens; components; three web shells; staff/patient mobile shells; route ownership; menu eligibility; status states; EN/FR; visual snapshots at 320–1920px.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P03 — Security, tenancy and authorization foundation

Modules: cross-cutting.

OIDC/MFA; facility/role/shift context; RBAC/ABAC; patient-scope authorization; consent policy engine foundation; tenant isolation tests; audit capture and command deduplication.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P04 — Facility and workforce operations foundation

Modules: M29, M33, M39.

Facility/configuration lifecycle; staff roles and privileges; hospital-cell provisioning and routing; capability-based sidebar and mobile More sections.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P05 — Registration, identity and consent

Modules: M01, M32.

Patient search/registration/provisional identity; identity merge review; consent/disclosure lifecycle; patient-context guard.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P06 — Scheduling and emergency intake

Modules: M02, M03.

Appointments, resource scheduling, triage prioritization and queues, staff mobile triage/vitals entry as permitted by authoritative APIs.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P07 — Clinical chart and ADT

Modules: M04, M05, M06.

Encounters, admission/transfer/discharge, beds, notes, diagnoses, observations, vitals, signing/amendment and mobile bedside views.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P08 — Medication safety and nursing

Modules: M07, M08, M09, M10.

Allergy state; prescribing; medication safety revision; medication administration; nursing plans and handovers, barcode workflows with real service acknowledgement.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P09 — Laboratory and imaging

Modules: M11, M12.

Laboratory specimen lifecycle, results and critical acknowledgement; imaging orders/reports/PACS references; offline boundaries.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P10 — Pharmacy and transfusion

Modules: M13, M15.

Pharmacy stock, reservations, release and dispensing; blood bank chain of custody and bedside cross-checks.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P11 — Surgery and care transitions

Modules: M14, M16.

Theatre/anesthesia/recovery, checklists, referrals/transfers and receiving owner acknowledgement.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P12 — Specialty clinical packages

Modules: M17.

Eight specialty packages; scoped protocol, forms, authorizations, validation and clinical review for each.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P13 — Patient mobile and proxy portal

Modules: M18.

Patient/proxy enrollment, appointments, record access, notifications, documents, consent and Android/iOS secure session controls.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P14 — Billing, cashier and claims

Modules: M19, M20.

XAF currency accuracy, charge master, immutable invoices/ledger, cash/payments, insurer approvals/claims and reconciliation.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P15 — Procurement, stores, assets and HR

Modules: M21, M22, M23, M24.

Purchase-to-pay, stock, biomedical maintenance, rosters/HR/payroll, permission segregation and HIS integration.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P16 — Facility services and hospital intelligence

Modules: M25, M26, M27, M28.

Ambulance/transport, dietary orders, mortuary chain-of-custody, operational BI and fresh provenance-backed dashboards.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P17 — Terminology, documents and evidence

Modules: M30, M31, M34.

Clinically governed terminology/formulary/rules, forms/signatures, immutable audit/provenance and retention.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P18 — Interoperability, devices, offline and SRE

Modules: M35, M36, M37, M38, M40.

Versioned FHIR/HL7 integrations; device gateways; notifications; locally authoritative sync and conflicts; backup/restore, telemetry, downtime playbooks.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

## P19 — Whole-platform testing and scoped release

Modules: cross-cutting.

End-to-end web/mobile/API integration; representative load; hazard review; security tests; failure drills; disaster recovery; EN/FR/accessibility/visual and supervised hospital pilot certification.

**Exit gate:** design and code artefacts reviewed; relevant API/DB/unit/integration/UI/mobile/EN-FR/authorization tests executed; failures recorded, no unresolved critical gate; evidence and cumulative artifacts saved. Clinical safety gates require qualified clinical reviewers.

