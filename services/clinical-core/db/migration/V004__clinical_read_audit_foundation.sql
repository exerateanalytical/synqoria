-- P01-B0020. Proposed schema, NOT executed on PostgreSQL. Security-reviewed
-- roles and policy are required before ANY application access is granted.
CREATE TABLE clinical_read_audit (
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 event_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 patient_id uuid NOT NULL,
 operation text NOT NULL CHECK (operation = 'patient:read'),
 occurred_at timestamptz NOT NULL,
 correlation_id uuid NOT NULL,
 PRIMARY KEY (tenant_id, facility_id, event_id),
 FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id,facility_id),
 FOREIGN KEY (tenant_id, patient_id) REFERENCES patient_master_index(tenant_id,patient_id)
);
CREATE INDEX clinical_read_audit_patient_lookup ON clinical_read_audit
 (tenant_id, patient_id, occurred_at DESC);
-- Ownership/application roles MUST be separated; table owners are not application roles.
ALTER TABLE clinical_read_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_read_audit FORCE ROW LEVEL SECURITY;
REVOKE ALL ON clinical_read_audit FROM PUBLIC;
-- No permissive RLS policy: even well-scoped SQL does not imply DB authorization.
-- Audit custody, privileged append-only enforcement, encryption, retention and
-- evidence anchoring remain release blockers.
