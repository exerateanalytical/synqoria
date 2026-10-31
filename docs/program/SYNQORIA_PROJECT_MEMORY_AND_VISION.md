# SYNQORIA — Project Memory, Vision & Engineering Constitution

**Document:** SYNQORIA-PM-001 · **Version:** 1.1 · **Recorded:** 31 October 2026 (Africa/Douala)  
**Programme:** OPES Health Systems / Opesware Technologies · **Product:** SYNQORIA  
**Official identity:** **SYNQORIA — Africa medical intelligence synchronized.**  
**Purpose:** Durable, cross-conversation project memory for product, architecture, clinical governance, UI/UX, implementation, testing and rollout. This is a **memory and requirements synthesis**, not proof that the product is implemented, validated, certified or approved for healthcare deployment.

## 0. Authority, source precedence and anti-hallucination contract

Read the **live** repository and evidence at the beginning of every implementation session. This document collects enduring intent and **does not replace** the authoritative specification, source code, current handoff or test registers. For conflicts, preserve these sources in order:

1. Documented user decisions and formally approved architecture decisions (ADRs), together with applicable clinical/legal safety requirements.
2. `docs/authority/SYNQORIA_Platform_Architecture_v2.1.md` — platform architecture, canonical 40-module authority and safety rules.
3. `docs/authority/SYNQORIA_UIUX_Master_v1.1.md` — 261 canonical screens, Warm Clinical design tokens, routes, responsive behavior and interaction rules.
4. `docs/program/PHASES.md` — P00–P19 programme phases; authoritative implementation ledger, screen register, tests and migration history.
5. `docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md`, `AGENTS.md` and fresh GitHub `main` — cross-chat coordination, latest head and actual work ownership.
6. This project memory — vision, product relationships, goals and decisions discovered across OPES digital-health work. Update this file by reviewed versioned commit; do not use memory alone to rewrite a specification.

**Status vocabulary is mandatory:** `vision / proposed / specified / source implemented / unit verified / integration verified / clinically accepted / security accepted / production qualified / deployed`. Never collapse these statuses. Do not claim a feature is built because its screen is listed or an isolated unit test passes. Do not invent clinical approvals, hospital agreements, privacy authorizations, compliance certificates, benchmarks or GitHub commits.

## 1. Founder-level vision and mission

**Vision:** Make SYNQORIA a dependable, sovereign, modern, connected health operating system designed for African health institutions, with a practical Cameroon-first rollout and capacity to expand across Africa. It should connect the patient journey from registration to care, diagnostics, medication, nursing, billing, referral and follow-up; strengthen hospital operations; and enable *authorized, privacy-preserving* health intelligence across institutions.

**Mission:** Help public, private, faith-based, rural, district, referral, specialized and teaching hospitals, clinics and medical networks deliver safer, faster, better coordinated care using electronic records, human-centered workflows and reliable institutional technology. Digitization must improve the work clinicians already do, not simply reproduce paper forms on a screen.

**Positioning:** A commercial, production-grade Electronic Medical Record (EMR), Hospital Information System / Hospital Management System (HIS/HMS), clinical workflow and operational infrastructure platform; **not** a mockup, limited demonstration or unreviewed medical decision-maker.

**Outcomes we are building toward:**

- One appropriately scoped patient and encounter view across authorized departments, sites and connected services, with sound identity/duplicate handling and consent-based disclosure where applicable.
- End-to-end hospital clinical operations including pre-consultation/triage, appointment and patient flow, ADT, clinical charting, medication safety, diagnostics, pharmacy, surgery, nursing, specialty care, patient engagement, billing, claims, procurement, workforce and hospital analytics.
- Resilient hospital-local, hybrid and centrally hosted operation, including defined safe workflows during internet interruptions, power/network failures, cell outages and delayed synchronization.
- An API product with governed interoperability to national and regional systems, insurers, diagnostic devices, partner software and patient platforms. No undisclosed exchange of patient data.
- Genuine patient safety, privacy, accountable clinical rules, tamper-evident audit, configurable policies, recovery capability and independent clinical/security acceptance before use.
- Scalable national and multi-hospital growth, with workload and release guarantees demonstrated by real tests rather than projections.
- Partnership opportunities in research, training, digital-health workforce development, clinical validation and health-system capacity building — each subject to separate approval.

## 2. Ecosystem memory — what belongs to SYNQORIA and what stays separate

**Corporate context:** Opesware Technologies is the engineering organization; **OPES Health Systems** is its healthcare-sector initiative/business. The healthcare work predates the SYNQORIA name. The vision also covers related, distinct digital-health platforms. Preserve their separately owned products, repositories, users, legal bases and data boundaries.

| Existing initiative / idea | Core purpose | SYNQORIA relationship / rule |
|---|---|---|
| **SYNQORIA** | Hospital EMR/HIS/HMS, clinical and administrative platform | Primary product governed by the 40-module architecture. |
| **OPES EMR/HIS** | Earlier hospital clinical record/management product and workflows | Draw validated requirements and institutional learning into SYNQORIA; do not silently migrate patient data or assume source-code parity. |
| **OPES Health Triage / OPES Triage** | Patient pre-consultation, QR/intake, triage prioritization, nurse vitals, queue routing and handover; staff web/tablet/mobile workflows | Inform M03 and related workflows; integrate or consolidate only after code, license, ownership and clinical-safety reconciliation. Existing demo: `triage.opesware.com` (not production proof). |
| **OPES Laboratory / LIS** | Lab orders, specimen capture, sample chain, tests, result validation, technician workflows and reporting | Canonical M11; analyzers and tests connect through M36/M35. |
| **OPES Radiology / RADIS** | Imaging orders, scheduling, reports, specialist workflow and external PACS | Canonical M12; never assert a diagnostic PACS viewer has been clinically certified inside SYNQORIA. |
| **OPES Pharmacy IS / OPES DrugTrace** | Medicine dispensing, stock, expiry, batches and separate medicine traceability | M13 pharmacy and medication-safety flows; DrugTrace may integrate as a separately governed product. |
| **Pediatrician and other specialty information systems** | Specialty charts, protocols, forms and workflow | M17 specialty packages; clinical rule content requires specialty review. |
| **OPES CDSS / Python clinical rules research** | Future clinical decision support, controlled inference and evidence-based alerts | **Not approved for autonomous clinical decisions in the v2.1 baseline.** Treat as optional isolated later capability requiring safety case, versioned content, human clinical oversight and regulatory review. Do not silently introduce Python into the core Java stack. |
| **OPES CDMS** | Clinical data management / longitudinal analytical capability | Specify interfaces and governance independently; avoid unapproved collection or duplication. |
| **OPESCARE / Opecare** | Digital Health ID/MPI, longitudinal health passport, patient consent, authorized information exchange and service discovery | **Separate interoperable ecosystem**, not synonymous with SYNQORIA. Integrate by approved API/FHIR, consent/purpose and institution agreements; patient cross-matching never automatically authorizes clinical access. |
| **HECID / CDITI** | Independent identity verification/eKYC products | May be assessed for institutional onboarding/verification, but no implicit use of identity-document/biometric databases for clinical records; separate controller, approvals and security reviews. |
| **National health surveillance and public-health collaboration** | Aggregated morbidity, outbreaks, service capacity, drug utilization and quality/performance statistics for authorized partners | Future privacy-preserving reporting/analytics through approved policies, DHIS2/interoperability if contracted; no unrestricted patient-level government feed. |
| **Digital Health Academy / clinical living lab** | Training, research and hospital partnerships | Complementary institutional initiatives; do not claim accreditation, hospital endorsement or research authorization without documents. |

**Hospital engagement memory:** OPES Health Systems has explored clinical partnerships, pilots and proposals with hospitals/faith-based providers and other institutional stakeholders. A discussion, proposal, demo or submission is **not a signed pilot, installed production HIS, accreditation or certification**. Pilot scope, facilities, data controllers, devices, support arrangements, clinical review, price and acceptance must each be recorded separately.

## 3. Locked SYNQORIA architecture — preserve it

**Architectural style:** API-first, domain-driven design (DDD), **hospital-cell architecture**, modular transactional clinical core, selected independently deployed domain services, event-driven integration, and selective CQRS/read projections. Avoid both a distributed microservice for every screen and an unstructured monolith. **Each cell is an independently operated hospital or hospital-group deployment** with owned data and failure boundaries; it is not just a Kubernetes pod or a replica.

**Authoritative technical baseline (from architecture v2.1):**

| Concern | Decision / boundary |
|---|---|
| Web | **React + Next.js + TypeScript**; accessible EN/FR application, separate workforce, patient/proxy and platform-operations shells. |
| Native mobile | **React Native / Expo** where device capability and clinical safety fit; staff and patient experiences; enrolled devices, protected on-device data, bounded offline tasks. |
| Principal transactional backend | **Java + Spring Boot** modular clinical core. Select maintained LTS/runtime versions via ADR; do not replace with Laravel or another framework based only on convenience. |
| Query processing | Durable PostgreSQL-derived read models and selective CQRS. **Go query services are a conditional future optimization**, not a mandatory first-phase tier; add only with measured profiling and architectural approval. |
| API ingress | **Kong or functionally equivalent gateway**; versioned OpenAPI 3.1 contracts, authenticated tenant routing and reauthorization at each service. |
| Source of truth | **PostgreSQL** authoritative transactional state, service/module ownership, scoped FKs, optimistic version checks, protected migrations, tested HA/restore. |
| Events and CDC | **Transactional outbox + durable Kafka integration + Debezium CDC**, with consumer inbox/dedupe, delivery/replay policies and operational controls. Do not publish an event before its authoritative write commits. |
| Cache | **Redis** for strictly reconstructible acceleration, not patient/medication truth or the only durable queue. |
| IAM | **OIDC/OAuth2-compatible identity authority** (Keycloak is a candidate, not a blanket assumption); MFA, local continuity, scoped privilege/consent/purpose, revocation and auditable break-glass. |
| Data/documents/imaging | Encrypted object storage and approved PACS/DICOM integrations; stable content hashes, quarantine, retention, explicit access controls. |
| Infrastructure | Containerized hospital cells; **Kubernetes when operationally justified and qualified**. Network isolation, secrets/KMS, independent failure domains, safe rollouts and genuine backup restoration. |
| Observability | OpenTelemetry-compatible tracing/logging/metrics; Prometheus/Grafana and search/alerting components may be selected via ADR. No sensitive PHI in routine logs/telemetry. |
| Integration | Contracted **HL7/FHIR**, DICOM/PACS, devices/analyzers, authorized payer and DHIS2/public-health adapters, all with tested version/coding/terminology mapping. FHIR version is partner-specific, not casually assumed. |

**Hard technical invariants:**

1. One bounded authoritative writer per clinical aggregate/cell; no uncontrolled cross-cell concurrent patient edits or split-brain writes. Migration/restore must fence writers with evidence.
2. Essential point-of-care clinical transactions **never synchronously depend** on central management, central analytics or another hospital cell; only defined local-authority processes can operate during disconnection.
3. The clinical core keeps strong consistency for tightly coupled medication/safety/clinical changes; events, projections, cache and eventual consistency never authorize a clinical action by themselves.
4. Every command is authenticated, tenant/facility/patient/resource scoped, version checked, idempotency controlled and audited as required; permissions enforced by backend and database, not only hidden menu items.
5. PostgreSQL owns transactional truth; Kafka/Redis cannot be substitutes. Outbox/inbox handle retries, duplication, partial failure and reconciled recovery.
6. Signed records are amended with provenance, not overwritten. Identity merging is reviewable and lineage-preserving. Record unknown/uncertain facts faithfully; never invent clinical facts.
7. Durable audit/capture and protected independent archival; patient data residency, consent, legal basis, purpose, retention and disclosure controlled by applicable law and contracts.
8. Hybrid offline means **local-authoritative hospital server + bounded mobile drafts/queues/synchronization**. Offline writes require explicit ownership epochs, conflict review and safe state labels. They are not general last-write-wins edits.
9. Keep Java as the primary backend; extract independent services only for documented domain boundaries, scalability/operations needs and tested consistency design. New languages are exceptional ADR decisions.
10. Every architectural change needs a reasoned ADR, impact on diagrams/contracts/schemas/security/testing, owner and migration plan. Protect the v2.1 baseline unless formally superseded.

## 4. Canonical capability scope and screen count

**40 modules**, stable IDs M01–M40: 28 business/clinical (M01–M28) and 12 platform/governance (M29–M40). **261 canonical screens** = 12 shared + 249 module screens. **20 engineering phases** P00–P19. Do not reclassify dialog variants, mobile breakpoints, components or printed reports as additional screens. The authoritative register owns exact screen IDs, fields and counts; avoid fake placeholder screens.

### Clinical and patient journey — M01–M18

- **M01** Patient registration, MPI, provisional identity, governed duplicate merges.
- **M02** Appointments, scheduling, calendars and constrained resources.
- **M03** Pre-consult/triage, emergency intake, priority assessment, queues, referrals/handovers and reassessment.
- **M04** Encounters, admissions, discharges, transfers, beds (ADT).
- **M05** Clinical notes, diagnosis, documentation, signatures and revisions.
- **M06** Vitals, observations, measurements and time/units/provenance.
- **M07** Allergies, intolerances, reconciling unknown/not-assessed/confirmed states.
- **M08** Medication prescribing, orders, checks, reconciliation and cancellation.
- **M09** Medication administration and unique dose-event record keeping.
- **M10** Nursing care plans, shift handovers, care tasks and overdue work.
- **M11** LIS, orders, samples, analyzers, results, critical flags, verification/amendment.
- **M12** Imaging/radiology workflow, reports and PACS/DICOM integration.
- **M13** Pharmacy dispensing, stock/batch/expiry, reservations and reconciliation.
- **M14** Surgery, theatre, anesthesia and recovery.
- **M15** Blood bank, transfusion, custody and patient/unit checks.
- **M16** Referrals, transfers, care transitions and accountable receiving owner.
- **M17** Eight specialty packages: obstetrics/gynecology, pediatrics/neonatology, cardiology, dentistry, dermatology, endocrinology/chronic disease, oncology, rehabilitation. Subpackages are **not** extra canonical modules.
- **M18** Patient portal/mobile/proxy access, patient records, reminders, grants and documents.

### Financial, hospital operations and workforce — M19–M28

- **M19** Charges, cash desk/cashier, receipts, invoicing, payments, XAF reconciliation and immutable financial ledger.
- **M20** Insurer/payer eligibility, prior authorizations, claims, remittance and appeals.
- **M21** Procurement, suppliers, purchase orders, approval and receiving.
- **M22** General stores and non-medication stock.
- **M23** Assets, biomedical equipment maintenance and facilities.
- **M24** Human resources, staffing, rosters, attendance and payroll, with clinical privileges separated from HR status.
- **M25** Ambulance, transport and dispatch.
- **M26** Nutrition/dietary instructions and catering.
- **M27** Mortuary and authorized decedent release, chain of custody.
- **M28** Hospital operations intelligence, evidence-backed KPIs and dashboards.

### Trust, interoperability and operations — M29–M40

- **M29** Facilities, departments, locations and service configuration.
- **M30** Terminology, medication formularies and approved versioned clinical rule packages.
- **M31** Forms, templates, immutable documents and signatures.
- **M32** Consent, privacy, patient disclosure policy and proxy governance.
- **M33** Identity, authentication, access, workforce privileges and MFA.
- **M34** Audit, provenance, retention and protected evidence.
- **M35** FHIR/HL7 interoperability, partner API product, webhooks, developer onboarding and replay-safe exchange.
- **M36** Medical devices, analyzer gateways, bedside devices and patient-device matching.
- **M37** Notifications, communications, priority escalation and acknowledgements.
- **M38** Offline synchronization, ownership/fencing epochs, conflicts and reconciliation.
- **M39** Tenant/cell lifecycle, service routing and fleet releases.
- **M40** HA/DR, backup/restoration, reliability engineering, telemetry, incident workflows.

## 5. UI/UX master memory — Warm Clinical, mandatory quality bars

**Source of truth:** `docs/authority/SYNQORIA_UIUX_Master_v1.1.md`. The initial baseline is **light clinical theme**; dark mode requires its own contrast and clinical-safety matrix, not naïve automatic inversion.

**Colour rules:** canvas `#F7F5F0` (warm cream), work surface `#FFFFFF`, warm subsection `#F0EEE7`, navigation/brand `#073656`, primary action `#1664D4`, body text `#172B4D`, secondary text `#475569`, selected surface `#E8F0FE`, meaningful borders and reserved semantically named success/warning/danger/info colours. All status foreground/background combinations come from the approved token register. Main content layers: cream page → white work panel → optional warm grouping → white field → readable dark text.

**Typography and interaction:** locally hosted **Inter**; base 16px with 1.5 line-height, form labels 14px, clinical values ≥16px; page titles 28/36 desktop and 24/32 mobile; responsive rem sizing, accessible zoom. Project minimum touch area 44×44px, bedside actions 48px, rows generally ≥48px. Spacing scale 4–48px; Lucide outline iconography with appropriate size/labels. Capability-scoped sidebar, tasks/quick actions before decorative statistics, explicit patient context and urgent exceptions.

**Non-negotiable visual and functional restrictions:** **NO glow/neon, gradients on working UI, glassmorphism, color collision, stretched assets, cramping, squeezing, low-contrast text, hidden/truncated critical medication data, overshrunk fonts, decorative clutter or generic AI-generated-looking cards**. Prevent content overlap across browser widths, text scaling, data density and mobile/tablet/desktop. No color-only severity signals. Show safe empty/loading/offline/error/conflict states; local draft, saved server-side, pending sync, synchronized and outcome unknown must be distinct. Form inputs, tables, alerts, dialogs, navigation, print views and role permissions need genuine real-content evaluation.

**Clinical navigation:** three separate shells (hospital workforce, patient/proxy, platform operations); no role automatically grants access to clinical data. Module membership and workflow detail pages belong under their correct parent. Keep the sidebar limited to genuine top-level tasks rather than making every detail view a navigation item. Patient switching requires unsaved-draft protection, auth recheck and cache clearing; no patient facts in public URLs/analytics.

## 6. Patient safety, security, compliance and ethics

This platform is health-critical software. **Clinical safety before speed or cosmetic completion.** Maintain fail-closed release controls even if formal certification is deferred during development.

- Patient identity: unknown patients and verified identity reconciliation; audit every merge/unmerge; no forced invented demographics. Verify patient at each critical order/dispense/administration.
- Medication safety: synchronized safety revision and short local transactions for orders/allergy/medication mutations; authoritative rule versions; pharmacist/clinician review, override reasons and safe cancellation semantics. No automated repeat physical handover after uncertain network results.
- Laboratory and urgent findings: real specimen lifecycle, critical result escalation, accountable clinician acknowledgement, no conflation of SMS delivery with medical acknowledgement.
- Clinical authorship: immutable signed revisions, retained history, meaningful dates, signers, provenance, document/template versions; correction is not destructive overwrite.
- Access: OAuth2/OIDC, MFA, audited patient/resource/purpose/tenant checks, locally supportable revocation, RBAC/ABAC, policy governance, confidential emergency-access procedure and deny-by-default behavior.
- Privacy: Cameroon health/data legislation and institution contracts need jurisdiction-specific legal review. Data protection impact assessments, patient notices, lawful basis, sensitive data controls, encryption at rest/in transit, retention/deletion rules, secure backups, incident response and processor/subprocessor governance must be implemented and evidenced. WHO, national policy, FHIR and OHADA references are targets of alignment **not automatic certification**.
- Finance: exact XAF monetary arithmetic, approvals and separation of duties, immutable postings/reversals, OHADA/SYSCOHADA-relevant reporting where applicable; no unreviewed financial state changes during downtime.
- Clinical AI/CDSS: only clinician-reviewed, versioned, explainable assistive rules after independent hazard assessment; no autonomous diagnosis/treatment in the baseline and no fictional AI safety claims.
- National surveillance: hospital-signed agreements, minimum necessary data, aggregate statistics, suppressed/restricted small counts when needed, security/legal approvals, consent where required and transparent governance. Facility-level statistics must not expose individuals; do not assume blanket consent authorizes all secondary uses or transfers.
- Independent acceptance: medical officers/clinicians, pharmacists, lab/radiology owners, privacy/security leads and hospital governance must approve their respective workflows before clinical pilot activation.

## 7. Scale, resilience and measurable nonfunctional goals

The ambitious target communicated is **hospitals capable of very high volume (up to 1,000,000 patients/encounters per month)**; v2.1 explicitly distinguishes **NETWORK-1M** (one million visits/month across fleet) from **HOSPITAL-1M** (one million visits/month at a single hospital). Test both separately before claiming either capacity. Clarify registered patients versus monthly visits versus encounters in contractual metrics; these counts are not interchangeable.

Design objectives: tested concurrency, safe admission control/load shedding, controlled p95/p99 latency by endpoint, durable command write throughput, failure-domain isolation, resilient queue/CDC, fast user workflows, tested HA, measured RPO/RTO, in-place upgrades without unsafe patient state loss, offline continuation and documented restoration. Targets become obligations only after reproducible performance/security/failure tests against realistic datasets and hardware.

**Deployment tiers:** on-prem hospital cell; hybrid on-prem with controlled burst/synchronization and approved external services; managed/dedicated data center or cloud cell; authorized networks/fleets. Data localization and chosen tenancy isolation are determined by contract and law. Avoid promises of 100% uptime or '10000% production readiness'.

## 8. Product and partnership delivery outcomes

**Cameroon-first delivery:** support English and French, XAF, local workflows, low-bandwidth or intermittent internet, mixed clinical staffing and realistic operations including power redundancy and on-site support. Progress from supervised pilots and measured hospital value to larger local groups, referral networks and regional deployments.

**Institutional partnerships:** hospital-owned problem definition → baseline and gap assessment → signed scope/data roles → requirements sign-off → localized installation/integration → supervised workflow pilot → user training → clinical/user/security acceptance → measured operational outcomes → scaled rollout. Exploratory meetings and submitted proposals must not be counted as deployments. The Digital Health Academy, research/living-lab projects and national statistics partnerships are distinct agreements and governance programs.

**Business goals:** financially sustainable commercial platform, repeatable implementation/support, institution-appropriate licensing/on-prem/SaaS options with transparent costs, secure API partnerships, staff training and long-term maintainability. Price/market-share projections are not fixed in this memory without verified decisions.

## 9. Formal 20-phase delivery track

**P00** Recover & verify cumulative source and scope; **P01** monorepo/contracts/migrations/CI; **P02** design system, navigation and cross-platform shells; **P03** security/tenancy/authorization; **P04** facility and workforce; **P05** patient registration, identity, consent; **P06** scheduling and triage; **P07** clinical chart/ADT/vitals; **P08** allergies/prescribing/administration/nursing; **P09** LIS/radiology; **P10** pharmacy/blood; **P11** surgery/referrals; **P12** specialty packages; **P13** patient/proxy mobile; **P14** billing/insurance; **P15** procurement/stores/assets/HR; **P16** support services/intelligence; **P17** terminology/forms/audit; **P18** interoperability/devices/offline/SRE; **P19** end-to-end acceptance/security/resilience/clinical release.

**Every module/screen must acquire:** approved user/clinical requirements; role/field/workflow/transition design; domain owners; API/OpenAPI/event/schema versions; migration and backout; accessible EN/FR responsive web/native implementation; boundary/scope/negative tests; persistence and integration proof; synthetic and authorized real clinical acceptance; support/observability/DR records; independent security/clinical gate as applicable. No count reaches 100% because source stubs exist.

## 10. Engineering cadence, batching and multi-chat coordination

- User preference: execute **up to 60 substantive engineering batches per requested run**, rather than one, when available; **count only completed and individually verified implementation batches**. Distinguish batch quantity from feature/module/screen completion. Preserve tested source and archive SHA-256 plus executed command/exit evidence.
- Parallel coding chats are working on the **same SYNQORIA** product. Before changes, check new GitHub head, `AGENTS.md`, `SYNQORIA_MULTI_CHAT_HANDOFF.md`, phase ledger, source differences, current Library ZIP and other known checkpoint; coordinate separate branches/non-overlapping batch IDs. Never assume another chat got a message automatically; use versioned shared handoff commits.
- Do not discard unique unmerged legacy changes or blindly merge conflicting migrations. Review, port and retest; maintain lineage for any divergence.
- Never let a development infrastructure blocker stop **independent safe work**; log it transparently and move to other verified work. However, do **not** evade database integrity tests, authorization, clinical locks or safety/release gates.
- User has deferred **formal certification** during ongoing engineering; **this does not allow clinical deployment, unreviewed clinical actions or claims of clinical readiness**. Restore full acceptance and certification prior to clinical use.
- End each run with actual files changed, branch/commit, evidence/tests pass/fail, blockers and next unreserved work; publish handoff. Save cumulative archived source and machine-verifiable hashes where feasible.

## 11. Verified status snapshot — 31 October 2026 (not self-updating)

- Shared repository: `https://github.com/exerateanalytical/synqoria` on `main`.
- Most recent observed source-management commits when this memory was prepared: `01da1dc7607908bafb005fd2de98ecf19eb73439` (`AGENTS.md` coordination) and `3944aa0f2eda9f245b7e3049687426140fde4dfa` (shared cross-chat handoff). They are **documentation commits, not new clinical features**.
- Latest cross-chat handoff describes verified cumulative engineering checkpoint **v0.57.0**, with latest recorded batch **P06-B0368** and candidate **P06-B0369** pending fresh ledger confirmation. v0.27.1 divergent historical material still requires selective review.
- **40/40 modules and 261/261 screens are specified as canonical inventory**. This does *not* make them implemented. As recorded in handoff: **0/261 clinically accepted screens; 0/40 certified modules; clinical release denied.**
- Outstanding independently proven gaps: live PostgreSQL/RLS restricted-role integration; production identity OIDC/MFA/consent/revocation; KMS and protected operational keys; reliable Next/Expo production compilations and physical-device tests; full responsive/accessibility evidence; high-volume benchmarks, outage/restore/HA tests; regulatory/security and clinician sign-off.
- `docs/program/STATUS.md` may lag the newer cumulative handoff (observed v0.56.0 text despite v0.57.0 source evidence). Resolve using fresh Git history and test registers, not assumption. **Never use this dated snapshot as current status in later sessions without verifying.**

## 12. Definition of achieved SYNQORIA vision

SYNQORIA reaches its intended first regulated hospital deployment only when real staff can safely follow the integrated patient journey from correctly identified patient → intake/triage → encounter and clinical documentation → ordered/verified laboratory and imaging → clinically reviewed medication/dispensing and nursing → billing/claims and discharge/referral → patient follow-up; all enabled parts behave correctly across EN/FR web/mobile, appropriate hospital offline modes, identity and audit boundaries, and independent performance/security/clinical tests.

A larger national/pan-African platform becomes legitimate only as authorized facilities deploy, measurable clinical/operational results improve, interoperable identities and partner interfaces are governed, dashboards are statistically valid and privacy-protected, and each high-volume cell can independently survive and recover from realistic failures.

**Permanent project rule:** **Build for real clinical operations. Preserve SYNQORIA's existing architecture and design authority. Verify every claim. Never trade patient safety, data sovereignty or correctness for batch counts, appearance or faster reports.**

## 13. Source-conversation reconciliation — 31 October 2026 (v1.1)

**Coverage rule:** This section records which historical specification conversations were recoverable through conversation-history summaries, which exact repository specifications were directly read, and what cannot yet be independently certified. It is not a representation that every Work mode conversation transcript has been opened line by line.

### 13.1 Original architecture/specification conversation

**“Assess EMR HMS Architecture” (23–27 October 2026):** recovered decisions and source-level cross-check with the actual repository files:

- Product identity: **SYNQORIA — Africa medical intelligence synchronized**.
- Primary architecture: API-first, DDD, independently operated **hospital cells**; a modular Java/Spring Boot transactional clinical core supplemented by selected domain services, PostgreSQL as the clinical source of truth, outbox/inbox and Kafka/Debezium event distribution, selective CQRS projections, Redis for acceleration only, Kong/equivalent at the edge, OIDC, TypeScript/Next.js web and Expo/React Native mobile.
- Scale: explicit, **separately benchmarked** NETWORK-1M and HOSPITAL-1M encounter-per-month planning profiles; targets are not achieved benchmarks.
- Scope: exactly **40 canonical modules**, not 15 deployment groups or a separate service for each module.
- UI/UX deliverable: **261 canonical screens** (12 shared and 249 module screens), module flows and governed screen IDs; workforce, patient/proxy and platform-operations applications; responsive, bilingual, capability-based navigation.
- Visual requirements: the approved **Warm Clinical** palette and component rules in `docs/authority/SYNQORIA_UIUX_Master_v1.1.md`, not earlier OPES HMS palettes. Primary canvas #F7F5F0; white work surfaces; navigation #073656; primary action #1664D4; Inter; Lucide. No glow, colour collisions, glass effects, destructive compression, distorted assets or hidden critical clinical details. Explicit error, pending synchronization, committed and unknown outcomes.
- All screens require exact owner, role/action permissions, inputs, field semantics, UI states, cross-screen transitions, API contracts, clinical risk, tests, EN/FR and mobile/accessibility behavior before being counted as implemented. Missing medical thresholds, terminology mappings and legal wording must not be fabricated.

**Reconciliation:** The above is incorporated in §§1–7 and the GitHub authority files. The original chat is a requirement provenance source; architecture v2.1 and UI/UX v1.1 remain the actionable normalized specifications. A request such as “what have we not planned?” should trigger a gap matrix, not a unilateral stack change.

### 13.2 Separate planning/build and Work mode context

**“Plan Autonomous Build Phases” and “SYNQORIA Hourly Engineering” (29–31 October 2026):** recoverable history confirms user priorities of (a) all 40 modules / 261 screens, (b) the staff and patient mobile apps alongside the web platform, (c) clean navigation and realistic clinical UI, (d) cumulative batches up to 60 *genuinely implemented and individually verified* items when requested, and (e) coordinated, non-conflicting commits from parallel coding chats. Earlier provisional planning discussed 462 batches; **this figure is not a fixed canonical implementation count**, whereas P00–P19 and the actual batch ledger are authoritative.

The exact standalone **Work mode specifications conversation transcript was not directly retrievable in this reconciliation**. Its unique requirements should be imported only after obtaining the original transcript or a verified specification/commit, with a source-to-requirement crosswalk and architecture/UI conflict review. Do not say it has been exhaustively imported until that happens.

The actual engineering status is not inferred from conversational claims of “next 60 batches”: GitHub current head, shared handoff, implementation ledger, source files and independently run tests determine progress. Formal clinical certification may be scheduled later, but clinical operations remain release-denied without clinical/security authorization.

### 13.3 Wider OPES electronic-health specifications checked against SYNQORIA

- The broader **OPES Health Systems product portfolio** groups identity/MPI/interoperability, EMR/triage/CDSS, specialty and departmental laboratory/pharmacy/radiology, billing/HMS, training and analytics. These provide product context but do **not** add extra SYNQORIA canonical modules without a documented scope decision.
- The older **OPESCARE full PRD** described identity, consent, longitudinal record, cross-hospital exchange and bridge integration. It remains a distinct product architecture/codebase, not authorization to replace the SYNQORIA stack with the older Laravel or offline .NET prototypes.
- The older **OPES Triage redesign** described many workflow-specific screens. Its queue, nurse-vitals, emergency triage, department handover and tablet/mobile patterns can inform M03, but its 81-screen inventory must not be added wholesale to SYNQORIA's 261-screen baseline.
- The proposed **National Health Watch** connects permitted aggregated indicators from SYNQORIA/EMR, OPES Triage, laboratory and pharmacy, with hospital participation agreements, DHIS2/IDSR mapping where approved, minimum-necessary reporting and no live feed without required authorization.
- Prior OPES offline-EMR prototypes and their different .NET/SQLite technology choices are historical requirements sources for local continuity and synchronization, **not** approval to change SYNQORIA's current Java/PostgreSQL architecture.
- Earlier design directions for OPES products (different backgrounds and primary colours) must not override the exact SYNQORIA Warm Clinical tokens.

### 13.4 Ongoing source audit and inclusion acceptance

Before declaring that the *entire account's SYNQORIA specifications* are incorporated, require: **(1)** an inventory of original architecture chat, standalone Work mode chat(s), planning/build chats and uploaded specs; **(2)** document/commit or transcript reference for each material requirement; **(3)** a mapping to architecture v2.1, UI/UX v1.1, canonical module/screen register or a new proposed ADR; **(4)** an explicit conflict/gap log; **(5)** a reviewed versioned memory commit. No requirement may silently modify the canonical framework, technology stack, count of modules/screens, clinical safety or data governance.

**Snapshot of this reconciliation:** original architecture and UI/UX specifications verified from canonical repository files; known planning/build-chat requirements cross-checked against the shared handoff; full standalone Work mode chat **not independently inspected**.
