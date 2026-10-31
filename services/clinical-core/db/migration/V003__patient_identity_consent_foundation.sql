-- P01-B0005. Draft ONLY; NOT executed. No identity matching or clinical consent
-- policy is implied by this schema. Avoid plaintext national ID in indexes.
CREATE TABLE patient_master_index (
  tenant_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  home_facility_id uuid NOT NULL,
  identity_status text NOT NULL CHECK (identity_status IN ('PROVISIONAL','VERIFIED','MERGED')),
  merged_into_patient_id uuid,
  revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
  PRIMARY KEY (tenant_id, patient_id),
  FOREIGN KEY (tenant_id, home_facility_id) REFERENCES hospital_facilities(tenant_id, facility_id),
  FOREIGN KEY (tenant_id, merged_into_patient_id) REFERENCES patient_master_index(tenant_id, patient_id),
  CHECK ((identity_status = 'MERGED') = (merged_into_patient_id IS NOT NULL)),
  CHECK (merged_into_patient_id IS DISTINCT FROM patient_id)
);
CREATE TABLE patient_identifier_tokens (
  tenant_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  identifier_system text NOT NULL CHECK (length(btrim(identifier_system)) BETWEEN 1 AND 120),
  identifier_token bytea NOT NULL CHECK (octet_length(identifier_token) = 32),
  PRIMARY KEY (tenant_id, identifier_system, identifier_token),
  FOREIGN KEY (tenant_id, patient_id) REFERENCES patient_master_index(tenant_id, patient_id)
);
-- identifier_token MUST be an approved keyed blind index computed by a trusted
-- service, not a plain hash of a low-entropy identifier.
CREATE TABLE patient_consent_records (
  tenant_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  consent_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  purpose_code text NOT NULL CHECK (length(btrim(purpose_code)) BETWEEN 1 AND 100),
  operation_code text NOT NULL CHECK (length(btrim(operation_code)) BETWEEN 1 AND 100),
  status text NOT NULL CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
  effective_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  revision bigint NOT NULL CHECK (revision > 0),
  PRIMARY KEY (tenant_id, patient_id, consent_id),
  FOREIGN KEY (tenant_id, patient_id) REFERENCES patient_master_index(tenant_id, patient_id),
  FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id),
  CHECK (expires_at > effective_at),
  CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),
  CHECK (revoked_at IS NULL OR revoked_at >= effective_at)
);
ALTER TABLE patient_master_index ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_master_index FORCE ROW LEVEL SECURITY;
ALTER TABLE patient_identifier_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_identifier_tokens FORCE ROW LEVEL SECURITY;
ALTER TABLE patient_consent_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_consent_records FORCE ROW LEVEL SECURITY;
REVOKE ALL ON patient_master_index, patient_identifier_tokens, patient_consent_records FROM PUBLIC;
-- No RLS allow policy. Merge/consent semantics and legal basis require review.
