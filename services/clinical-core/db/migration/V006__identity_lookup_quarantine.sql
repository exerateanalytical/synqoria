-- P05-B0010 source-only, NOT applied to PostgreSQL. Deliberately no RLS policies or role grants.
-- Identity lookup must be populated only by reviewed, server-side HMAC indexing service.
CREATE TABLE patient_identity_lookup (
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 lookup_hmac bytea NOT NULL CHECK (octet_length(lookup_hmac)=32),
 patient_id uuid NOT NULL,
 candidate_revision bigint NOT NULL CHECK (candidate_revision >= 0),
 PRIMARY KEY (tenant_id, facility_id, lookup_hmac, patient_id),
 FOREIGN KEY (tenant_id, patient_id, facility_id) REFERENCES patient_master_index(tenant_id, patient_id, home_facility_id)
);
CREATE INDEX patient_identity_lookup_revision_idx ON patient_identity_lookup(tenant_id, facility_id, lookup_hmac, candidate_revision DESC);
ALTER TABLE patient_identity_lookup ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_identity_lookup FORCE ROW LEVEL SECURITY;
REVOKE ALL ON patient_identity_lookup FROM PUBLIC;
-- No permissive policy until restricted role, audited lookup, rate limits and retention are approved.
