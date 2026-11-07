# SYNQORIA — Hospital Documents & Print Forms

**Status: SOURCE/PRINT-SAMPLE SYSTEM ONLY. Clinical production release: DENIED.**

## Scope and traceability

- 40 canonical hospital domains; module ownership follows UI/UX Master v1.1.
- **18 immutable canonical print identifiers, P01–P18**, retained exactly.
- **286 proposed additional hospital document/form types**, for **304 defined templates** in `registers/hospital-document-template-catalog-v1.json`.
- 72 reusable structured document field groups, 442 individual field descriptors, 35 typed table-column schemas.
- Server-side Next.js routes: `/documents` (filter/search catalogue) and `/documents/{templateId}` (304 sample documents). All output samples are blank and marked UNISSUED.
- Media intents: A4, A5, card, specimen/medication label and thermal receipt. **All browser-generated samples deliberately print on A4** until specific stock/barcode and receipt printers undergo calibration and validation.
- Intended languages: English and French; full medically accurate French translations are **not yet approved or implemented**. The prototype renders in English only.
- Documents contain source fields and section structures, **not filled patient or financial values**. Authorized editing, actual document issuance and signatures are not implemented.

## Canonical P01–P18

| Code | Document | Owning module | Primary safety consideration |
|---|---|---|---|
| P01 | Patient identity card | M01 | Two identifiers, provisional identity, correct patient scope |
| P02 | Appointment and preparation slip | M02 | Current schedule/version, correct instructions |
| P03 | Specimen collection label | M11 | Patient/specimen match, actual barcode and printer stock |
| P04 | Laboratory result report | M11 | Analyzer provenance, method, units, intervals, critical result and verifier |
| P05 | Radiology report | M12 | Study/modality ID, findings, interpretation, authenticated radiologist |
| P06 | Prescription | M08 | Coded medication, strength, dose/unit, route, frequency, duration, allergy check, authorization |
| P07 | Medication administration export | M09 | Unique dose identity, due/given/held/omitted states and witness |
| P08 | Discharge summary | M04 | Current signed plan, results and outstanding responsibilities |
| P09 | Referral letter | M16 | Disclosure authority, receiving clinician and acknowledgment |
| P10 | Invoice | M19 | Approved service catalogue, price/tax/current jurisdiction and numbering |
| P11 | Payment receipt | M19 | Confirmed settlement and durable transaction receipt; no guessed payment success |
| P12 | Consent copy | M32 | Procedure-specific options, understanding, capacity, legal authority, refusal and withdrawal |
| P13 | Operative summary | M14 | Procedure/site/team, specimens/implants, verified operative author |
| P14 | Transfusion monitoring record | M15 | Unit identity, compatibility, reaction and independent checks |
| P15 | Purchase order | M21 | Approved supplier, amount, maker-checker, financial authority |
| P16 | Goods received note | M22 | Received/rejected quantity, batch/expiry, custody |
| P17 | Decedent custody and release record | M27 | Legal authority, identity, uninterrupted custody lineage |
| P18 | Audit event export manifest | M34 | Actor, scope, purpose, immutable event provenance and export authorization |

### Important distinction

The WHO laboratory quality documents describe how laboratory procedures, forms and records should be controlled and tailored to the specific laboratory. HL7 FHIR DiagnosticReport and Observation are appropriate **interoperability mappings** for clinical lab/radiology results, not legal authorization to render a report. The WHO Surgical Safety Checklist can inform a facility-approved adapted surgical checklist; the source is referenced, not rebranded or reproduced as a certified original.

**References**

- [WHO Laboratory Quality Manual Template](https://www.who.int/publications/m/item/laboratory-quality-manual)
- [WHO Laboratory Quality Management System handbook](https://www.who.int/publications/i/item/9789241548274)
- [HL7 FHIR R5 DiagnosticReport](https://hl7.org/fhir/diagnosticreport.html)
- [WHO Surgical Safety Checklist resources](https://www.who.int/teams/integrated-health-services/quality-of-care-and-patient-safety/patient-safety-guidance-and-tools/safe-surgery/tool-and-resources)

## Issue lifecycle contract (NOT YET IMPLEMENTED)

1. Read hospital cell, actor, role, current patient/encounter and source entity identity from **server-verified** context.
2. Enforce template version, facility policy, field dictionary, complete data types, validation, role permissions, and purpose-specific disclosure policy.
3. On issuance, read current PostgreSQL authoritative data and record its source version; reject stale/conflicting revisions. For critical clinical content, require independent review where stipulated.
4. Use approved template locale, issue number and document disposition. Sign with validated credential/service, **not an image of handwriting**.
5. Store immutable content hash, signer evidence, render version, source version, issue time, access context and audit/outbox event atomically.
6. Render a tagged/selectable PDF with safe page numbering, headers, language, print media and no orphaned dose/signature rows. Generate barcode/QR **only** from a verified, expiring, access-controlled reference and calibrated printer.
7. Provide permission-scoped retrieval and verification, retention/hold/revocation/disclosure controls. Amend as a new linked version, never silently replace an issued original.
8. Treat failed/timeout outcomes as unknown and reconcile by idempotency key before retry. Do not label an unconfirmed payment as paid or a clinically unsigned report as final.

## Tests in this workstream

- Node contract test: validates all 304 templates, the canonical 18, module ownership, 35 table schemas and strict sample-only gates.
- Next/TypeScript pipeline: builds the actual 304 template routes with the existing 261 UI screens.
- Chromium pipeline: verifies 304 HTTP routes, 608 mobile/desktop specimens, input/field completeness, no page-wide overflow or button glow/overlap, correct warning flags and blank PDF generation for **each** template. Artifact is an **unissued sample PDF library**, not a release.
- Screenshots, PDF hash manifest and browser audit are retained in Actions artifact for manual review.

## Pending approvals and blockers

- Per-hospital forms census against hospital paper forms and local legal/health ministry requirements; 304 is a **candidate baseline**, not proof every hospital's workflow is included.
- Clinical document types, mandatory validation, terminology, signature scopes and controlled approval.
- Cameroon-specific consent wording, professional qualification, mortality forms, insurer forms, DGI/OHADA/tax details, receipt numbering, issuer branding and fiscal validity, and retention rules.
- Verified role/tenant/patient scoping, data minimization, field-level provenance and per-template disclosure policies.
- Full EN/FR localization by qualified clinical/legal translators.
- Accessibility WCAG 2.2 AA including screen-reader and real print/PDF testing, real 320px reflow and PDF tagging.
- Receipt/label/card printer stock, barcode quiet zones, thermal dimensions, margins and read-back validation.
- Actual secure API integration, schema/versioned renderer, signatures, encryption, KMS, immutability/retention, audit and idempotent issuance.
- Human print content and layout acceptance, per-template risk assessment and clinical certification.

**Until those are complete:** no issuing actual prescriptions, diagnostic results, invoices, receipts, legally valid consents or medical certificates; `SAMPLE ONLY` remains mandatory.
