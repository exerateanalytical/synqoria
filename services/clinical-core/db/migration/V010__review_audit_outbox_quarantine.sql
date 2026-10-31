-- P05-B0053. UNAPPLIED draft; requires DBA, clinical security and retention review.
-- This adds no runtime access, policies, or grants. Deployment MUST be separately approved.
CREATE TABLE patient_registration_review_audit (
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 audit_event_id uuid NOT NULL,
 review_event_id uuid NOT NULL,
 review_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 outbox_event_id uuid NOT NULL,
 review_digest_sha256 char(64) NOT NULL CHECK(review_digest_sha256 ~ '^[0-9a-f]{64}$'),
 recorded_at timestamptz NOT NULL,
 PRIMARY KEY(tenant_id,facility_id,audit_event_id),
 UNIQUE(tenant_id,facility_id,review_event_id),
 UNIQUE(tenant_id,facility_id,outbox_event_id),
 FOREIGN KEY(tenant_id,facility_id,review_event_id)
  REFERENCES patient_registration_review_events(tenant_id,facility_id,event_id),
 FOREIGN KEY(tenant_id,facility_id,outbox_event_id)
  REFERENCES clinical_outbox(tenant_id,facility_id,event_id),
 FOREIGN KEY(tenant_id,facility_id) REFERENCES hospital_facilities(tenant_id,facility_id)
);
ALTER TABLE patient_registration_review_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_registration_review_audit FORCE ROW LEVEL SECURITY;
REVOKE ALL ON patient_registration_review_audit FROM PUBLIC;
-- NO permissive RLS policy, NO grants, NO trigger disabling, NO deployment certification.
