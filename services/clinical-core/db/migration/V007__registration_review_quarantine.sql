-- P05-B0013: REVIEW-ONLY DRAFT. Restricted non-owner role and controlled workflow NOT provisioned.
-- This table is intentionally denied to all application roles until the review workflow is implemented and approved.
CREATE TABLE patient_registration_review_decisions (
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 review_id uuid NOT NULL,
 reviewer_id uuid NOT NULL,
 registrar_id uuid NOT NULL,
 candidate_id uuid NOT NULL,
 candidate_revision bigint NOT NULL CHECK(candidate_revision >= 0),
 command_id uuid NOT NULL,
 patient_id uuid NOT NULL,
 fingerprint_hmac bytea NOT NULL CHECK(octet_length(fingerprint_hmac)=32),
 outcome text NOT NULL CHECK(outcome = 'NO_UNRESOLVED_MATCH'),
 decided_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 revoked_at timestamptz,
 key_id varchar(64) NOT NULL,
 mac bytea NOT NULL CHECK(octet_length(mac)=32),
 PRIMARY KEY(tenant_id,facility_id,review_id),
 FOREIGN KEY(tenant_id,facility_id) REFERENCES hospital_facilities(tenant_id,facility_id),
 CHECK(reviewer_id <> registrar_id), CHECK(expires_at > decided_at)
);
CREATE INDEX patient_registration_review_scope_idx ON patient_registration_review_decisions(tenant_id,facility_id,review_id,candidate_revision);
ALTER TABLE patient_registration_review_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_registration_review_decisions FORCE ROW LEVEL SECURITY;
REVOKE ALL ON patient_registration_review_decisions FROM PUBLIC;
-- No allow policy. No on-write approval, no reviewer permission, no expiry refresh.
