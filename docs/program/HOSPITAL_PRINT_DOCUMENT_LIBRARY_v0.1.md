# SYNQORIA — Hospital Document & Form Library v0.1

**Product:** SYNQORIA — Africa medical intelligence synchronized.  
**Scope:** 376 distinct coded, blank, printable **design specimens** across 24 service groups.  
**Repository lineage:** stacked on SYNQORIA UI PR #14.  
**Clinical, legal, financial and release status:** **NOT APPROVED. NO LIVE ISSUANCE.**

## 1. Authority and scope

This library extends the 18 mandatory printable outputs enumerated in `docs/authority/SYNQORIA_UIUX_Master_v1.1.md` §13. It does **not** add navigable clinical screens or change the canonical 40 modules / 261 screens. The register is a broad multinational hospital baseline, not a claim that all hospitals, ministries, professional bodies, insurers, laboratories or specialties use identical forms.

Every definition has a stable template code, English and French document title, department and canonical module, layout kind, subject context, 2–4+ separately authored information sections, and associated fields. A single generic document masquerading as many specialized documents is not accepted.

## 2. Inventory — 376 documents / 24 service groups

| Code | Department | Documents |
|---|---|---:|
| PAT | Patient administration / reception | 20 |
| OPD | Outpatient consultations / clinical notes | 18 |
| EMR | Emergency medicine and triage | 16 |
| NUR | Nursing / bedside care | 19 |
| LAB | Laboratory and pathology | 24 |
| RAD | Radiology and imaging | 14 |
| PHM | Pharmacy and medication safety | 15 |
| SUR | Surgery and anesthesia | 18 |
| BLO | Blood bank and transfusion | 11 |
| MAT | Maternity / obstetrics / neonatology | 16 |
| PED | Pediatrics | 10 |
| CON | Consent / authorization / privacy | 21 |
| FIN | Finance / cashier / receipts | 19 |
| INS | Insurance and payer claims | 12 |
| SUP | Procurement / suppliers / inventory | 17 |
| MOR | Mortuary and decedent custody | 11 |
| GOV | Audit / public health / privacy governance | 15 |
| FAC | Facilities and biomedical equipment | 12 |
| AMB | Ambulance and medical transport | 9 |
| HR | Human resources and hospital operations | 13 |
| QLT | Infection prevention and patient safety | 15 |
| REH | Nutrition, physiotherapy, rehabilitation, social care | 11 |
| SPC | Cardiology, oncology, dialysis, dental, neurology and other specialties | 22 |
| SYS | Identity access, terminology, FHIR, device, offline and cell operations | 18 |
| **Total** | | **376** |

### Canonical P01–P18 mapping

| Code | Printable output |
|---|---|
| P01 | Patient identity card |
| P02 | Appointment / preparation slip |
| P03 | Specimen label |
| P04 | Laboratory results report |
| P05 | Radiology diagnostic report |
| P06 | Prescription |
| P07 | Medication administration record |
| P08 | Discharge summary |
| P09 | Referral letter |
| P10 | Invoice |
| P11 | Payment receipt |
| P12 | Signed consent copy |
| P13 | Operative summary |
| P14 | Transfusion record |
| P15 | Purchase order |
| P16 | Goods receipt |
| P17 | Decedent custody / release record |
| P18 | Audit export manifest |

## 3. Implemented, and not implemented

### Implemented in this workstream

- Type-specific source registers under `apps/web/app/ui/printables/catalog-*.json`, aggregated into `registry.ts`.
- Searchable document library at `/ui/printables`, with 24 departments, category filters, keyword search across fields, canonical-only filter and pagination.
- Dedicated page `/ui/printables/{templateId}` per document definition.
- Real HTML/CSS blank form proofs with facility letterhead placeholder, document code, bilingual heading, subject/administrative context, authored sections and fields, specialized tables for laboratory reports, prescriptions, MAR, radiology, invoices, receipts, transfusions and procurement, plus consent-choice placeholders and role-aware signature lines.
- Paper proofs: A4 portrait, A4 landscape, A5, thermal 80-mm receipt **proof**, and A4 specimen-label **proof**. Browser printing is available only for clearly watermarked **blank samples**.
- No fake patient identifiers, dosage values, tax totals, ledger confirmations, clinical decisions, signatures, QR codes or scanner barcodes.
- CSS prevents glow/gradients, avoids compressing long values, wraps on narrow screens, repeats table headers in print and aims to prevent dose/unit/signature rows splitting.
- Fail-closed `issuance-gate.ts` records proposed server conditions and **always denies real issuance**.

### NOT implemented or approved

1. Hospital-specific approved clinical forms, data types, mandatory fields, local diagnosis and terminology bindings, reference intervals, clinical protocols, dosage checks or treatment instructions.
2. Legal validity of consent, medical certificates, controlled-substance prescriptions, professional signatures or authorized delegation.
3. Official Cameroonian tax/fiscal invoicing, OHADA posting, invoice numbering, DGI identifiers, e-receipt requirements, insurance formats, supplier contracts or payroll calculations.
4. Live database data binding, signed clinical results, ledger posting, authorized disclosure, document issuance service, patient identity proof, source-version conflict handling, immutable PDF attachment or audited print-job lifecycle.
5. Printer calibration: specimen labels and barcodes require verified size, media, printer DPI, symbology, human-readable identifiers and scan validation. The A4 label proof is **not** a printer-ready clinical label.
6. Complete EN/FR localization of specialized field names; every document title and standard chrome is bilingual, but technical field labels require medical translation review.
7. Qualified clinical, pharmacy, laboratory, privacy, legal, fiscal, security, accessibility and institutional sign-off.

## 4. Intended domain architecture, not implemented yet

A production document subsystem must be a separate bounded capability under M31, using a versioned template schema (immutable after approval) and **server-rendered or trusted service-rendered PDFs** backed by authoritative clinical/financial records. It must never scrape DOM, capture screenshots or accept user-visible display strings as a source of truth.

Planned authoritative entities:

- `DocumentTemplateDefinition` — stable ID, document family, locale, jurisdiction, specialty and owner.
- `DocumentTemplateVersion` — reviewed schema, content, styles, policy version, approval evidence and effective dates.
- `DocumentInstance` — source references (patient/encounter/order/ledger etc.), version, scope, status, provenance, layout checksum.
- `DocumentSignature` — verified signer identity, capacity, consent or signing basis, timestamp, cryptographic evidence, revocation/expiry handling.
- `DocumentIssueReceipt` — unique server-confirmed issue ID, exact source versions, idempotency key and immutable content digest.
- `DocumentAmendment` — corrected or voided version linked to original, justification, notification and retrieval controls.
- `DocumentDisclosure` — audience, legal purpose, per-tenant and per-resource authorization, expiration, revocation and audit.
- `PrintJobEvidence` — printer/media profile, actor, copy number, timestamp, delivery status; confidential spool handling.
- `DocumentRetentionPolicy` — jurisdiction, data classification, legal hold, export and destruction controls.

Proposed lifecycle: DRAFT → IN_REVIEW → APPROVED → ISSUED; post-issue changes create an amendment/correction or authorized void without silently rewriting the previous issued copy. A failed or timed-out response is **unknown outcome**, to be resolved by exact idempotency key and status lookup, never by generating a second invoice, receipt, label or prescription.

Server prerequisites: verified current actor/role/scope, facility/cell, patient/encounter (when applicable) with two approved identifiers, verified source data/version, template/version and medical content review, signing authority, consent and disclosure restriction, audit/outbox commit, server-issued receipt, label and printer validation, retention and traceability.

## 5. Domain-specific source contracts

- **Diagnostic reports:** align report status, patient, order, specimen, collection/performance/issue times, structured observations, coding, values with units and reference ranges, interpretation, performer and version; see [HL7 FHIR DiagnosticReport](https://hl7.org/fhir/diagnosticreport.html). The PDF/presented form must reflect the authoritative diagnostic observation data; no freeform value generation.
- **Medication prescriptions/administration:** source medicine identity/form/strength, actual dose and unit, route, frequency, duration, dispenser instructions, quantity, refill authority, allergy and interaction assessments; see [HL7 FHIR MedicationRequest](https://hl7.org/fhir/medicationrequest.html). Medication administrations and dispenses are separate events and must never be inferred from each other.
- **Consent:** document purpose and procedure, material risks/benefits, alternatives, grant/decline, capacity and proxy authority, signed evidence, duration, withdrawal and permitted disclosure; see [HL7 FHIR Consent](https://hl7.org/fhir/consent.html). A signature image is not legal proof.
- **Surgical checks:** high-level pre-anesthesia, pre-incision and before-exit safety stages are modeled as checklist items to be adapted and reviewed with local policy, reflecting [WHO Surgical Safety Checklist](https://www.who.int/docs/default-source/patient-safety/9789241598590-eng-checklist.pdf); no copyrighted checklist is reproduced.
- **Financial reports:** posted ledger, validated services, tax policy, payer splits and gateway confirmations must be authoritative. A browser-rendered total is never evidence of payment.
- **Identifiers/labels:** no home-made scannable codes in the prototype; issue human-readable and machine-readable identity labels only via validated identity/specimen services and printer profiles.

The specific FHIR version/profile for each integration must be selected in the actual OpenAPI/interoperability contract; citing a model here is not a FHIR conformance statement.

## 6. Acceptance and release gates

| Gate | Required evidence |
|---|---|
| PD01 Inventory | Reviewed hospital/specialty/insurer document checklist; stable codes and ownership |
| PD02 Data contract | Typed field dictionary, coding/unit and source-to-template bindings |
| PD03 Clinical review | Qualified sign-off for report, prescription, consent, surgical and transfusion details |
| PD04 Fiscal/legal | Cameroon/local statutory numbering, tax, consent and signatory review |
| PD05 Authorization | Tenant/cell, patient/resource, actor/role, purpose, consent and proxy controls |
| PD06 Audit/recovery | Transactional outbox, idempotency, amend/void and status lookup |
| PD07 Print media | A4/A5/thermal/labels, full-page and long tables, barcodes, printer calibration |
| PD08 Usability | 320/768/1440+ responsive checks, keyboard and screen-reader review, readable typography |
| PD09 Bilingual | Full approved EN/FR clinical and legal strings, fallback audit |
| PD10 Integration | Live PostgreSQL/source-of-truth and signed PDF/issued document tests |
| PD11 Security | No data in URLs/analytics, safe downloads/spooling, disclosure and retention |
| PD12 Acceptance | Hospital clinical/regulatory sign-off, independent evidence and signed release record |

Current approval: **all PD gates pending; no document can be issued as genuine**. Printing a blank specimen does not indicate any of these gates passed.
