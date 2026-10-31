# M03 Triage — authenticated HTTP and portable client transport (v0.56.0)

**Status:** Internal engineering evidence only. **NOT** clinical release, validated medical practice, approved patient-data processing, production OIDC or verified PostgreSQL RLS.

## Authority and scope

This implementation continues from the independently revalidated v0.55.0 cumulative Library checkpoint. Preserve 40 modules, 261 screen IDs and clinical safety release gates. There are **zero accepted clinical screens and zero certified modules**. No risk score, triage priority, medication dose or treatment decision is inferred or calculated in this transport.

## Implemented interfaces

- `POST /api/v1/triage/patients/{patientId}/cases/{caseId}/intake`: First-arrival creation with expected revision 0, verified identity and clinical evidence supplied by injected server-side dependencies; atomic case/genesis/audit/outbox/receipt when the underlying transaction port is atomic; replay returns 200 and first create 201.
- `POST /api/v1/triage/patients/{patientId}/cases/{caseId}/commands`: 49 existing domain actions mapped into typed, scoped, bearer-authenticated transport. Observation record/correction use a bounded strict JSON object with server-supplied `recordedBy` and append-only correction provenance. No arbitrary clinical priority rules added.
- `POST /api/v1/triage/patients/{patientId}/views/{view}`: Eight bounded, explicitly case-selected, patient-scoped list views. Each requires server-side verified patient access; input cannot select tenant, facility, staff identity or evidence. Case IDs are in the POST body, not the query URL. All responses omit free-text clinical details and are `Cache-Control: no-store, private`.
- Portable `@synqoria/ux-core/triage-client`: 49 typed action transports, eight view declarations, strict response decoding, short-lived in-memory token provider, disabled offline clinical writes, bounded request scope and no automatic credential forwarding.
- Versioned OpenAPI draft `contracts/openapi/synqoria-m03-triage-restricted-v1.yaml` matches Java action enum and is **not** a deployment attestation.

## Evidence and limitations

Every new P06-B0303–P06-B0362 feature has its own executable verification log. B0303–B0351 are distinct integrations of **preexisting** domain command operations, not 49 new Java algorithms. B0352–B0359 are eight new HTTP list endpoints; B0360 is intake creation; B0361/62 are typed observation/lineage acceptance. Synthetic loopback HTTP uses a fake verified identity and atomic store: **PostgreSQL, restricted-role RLS, actual authentication and physical device behavior remain unverified.**

Portable TypeScript and Java compile and their focused tests pass; no compiled Next.js or Expo binary is represented as ready. Backend clinical revocation, provenance, patient scope and permission checks must still be independently verified against real deployment dependencies. The source-level SQL migration gates are not substitutes for database integration or human security review.

## Mandatory next gates

1. `P06-B0363`: Test V019 PostgreSQL schema, restricted roles, forced RLS, cross-patient denial, true concurrent writers, rollback and idempotency replay under a real isolated database. Keep deny-all production grants until reviewed.
2. Integrate actual OIDC/MFA, the server-managed patient/consent clinical proof service and secure KMS key lifecycles.
3. Wire guarded staff web and native controls with authorized facility/patient context, EN/FR 320–1920px/200% scaling, full accessibility and safe session transitions. Generic locked screens do not count as clinically implemented.
4. Independent clinical, security and pilot sign-off before changing any release lock.

**Release status: DENIED.**
