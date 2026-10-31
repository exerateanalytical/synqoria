-- P01-B0031. DRAFT ONLY. Add append-only event chain to future restricted reviewer service.
-- Unapplied. No staff/clinical application role receives read/write privileges.
CREATE TABLE patient_registration_review_events (
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 event_id uuid NOT NULL,
 review_id uuid NOT NULL,
 event_version bigint NOT NULL CHECK(event_version >= 1),
 event_kind varchar(32) NOT NULL CHECK(event_kind IN ('REVIEW_APPROVED','REVIEW_REVOKED')),
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 candidate_id uuid NOT NULL,
 candidate_revision bigint NOT NULL CHECK(candidate_revision >= 0),
 audit_binding_sha256 char(64) NOT NULL CHECK(audit_binding_sha256 ~ '^[0-9a-f]{64}$'),
 occurred_at timestamptz NOT NULL,
 PRIMARY KEY(tenant_id,facility_id,event_id),
 UNIQUE(tenant_id,facility_id,review_id,event_version),
 FOREIGN KEY(tenant_id,facility_id,review_id)
   REFERENCES patient_registration_review_decisions(tenant_id,facility_id,review_id),
 FOREIGN KEY(tenant_id,facility_id) REFERENCES hospital_facilities(tenant_id,facility_id)
);
CREATE INDEX patient_registration_review_event_history_idx
 ON patient_registration_review_events(tenant_id,facility_id,review_id,event_version);
ALTER TABLE patient_registration_review_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_registration_review_events FORCE ROW LEVEL SECURITY;
REVOKE ALL ON patient_registration_review_events FROM PUBLIC;
-- Zero permissive policies: default deny. Restrictive service policy follows human-reviewed DDL deployment.
