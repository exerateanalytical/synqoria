-- M03 P06 secure transactional triage cell. QUARANTINED: NOT FOR CLINICAL RELEASE.
-- Requires reviewed, restricted DB roles/RLS policies, KMS, backups, retention, and live concurrency tests.
-- Unlike V015 legacy draft, this table holds encrypted aggregate bytes, not plaintext metadata.
CREATE TABLE synqoria_triage_secure_cases_quarantine (
 tenant_id uuid NOT NULL,facility_id uuid NOT NULL,patient_id uuid NOT NULL,case_id uuid NOT NULL,
 revision integer NOT NULL CHECK (revision >= 1),
 encryption_key_id varchar(80) NOT NULL,
 encrypted_snapshot bytea NOT NULL CHECK (octet_length(encrypted_snapshot) BETWEEN 29 AND 4200000),
 state_digest char(64) NOT NULL CHECK (state_digest ~ '^[0-9a-f]{64}$'),
 updated_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id,facility_id,patient_id,case_id)
);
CREATE TABLE synqoria_triage_secure_events_quarantine (
 tenant_id uuid NOT NULL,facility_id uuid NOT NULL,patient_id uuid NOT NULL,case_id uuid NOT NULL,
 revision integer NOT NULL CHECK (revision>1),command_id uuid NOT NULL,
 actor_digest char(64) NOT NULL CHECK (actor_digest ~ '^[0-9a-f]{64}$'),
 operation varchar(80) NOT NULL,previous_digest char(64) NOT NULL,state_digest char(64) NOT NULL,event_digest char(64) NOT NULL,
 occurred_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id,facility_id,patient_id,case_id,revision),
 UNIQUE (tenant_id,facility_id,patient_id,case_id,command_id),
 FOREIGN KEY (tenant_id,facility_id,patient_id,case_id) REFERENCES synqoria_triage_secure_cases_quarantine(tenant_id,facility_id,patient_id,case_id)
);
CREATE TABLE synqoria_triage_secure_outbox_quarantine (
 event_id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL,facility_id uuid NOT NULL,patient_id uuid NOT NULL,case_id uuid NOT NULL,
 command_id uuid NOT NULL,event_digest char(64) NOT NULL,event_type varchar(80) NOT NULL,created_at timestamptz NOT NULL,
 published_at timestamptz,
 UNIQUE (tenant_id,facility_id,patient_id,case_id,command_id),
 FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id) REFERENCES synqoria_triage_secure_events_quarantine(tenant_id,facility_id,patient_id,case_id,command_id)
);
CREATE TABLE synqoria_triage_secure_receipts_quarantine (
 tenant_id uuid NOT NULL,facility_id uuid NOT NULL,patient_id uuid NOT NULL,case_id uuid NOT NULL,
 command_id uuid NOT NULL,request_digest char(64) NOT NULL,revision integer NOT NULL CHECK(revision>=1),
 event_digest char(64) NOT NULL,committed_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id,facility_id,patient_id,case_id,command_id),
 FOREIGN KEY (tenant_id,facility_id,patient_id,case_id) REFERENCES synqoria_triage_secure_cases_quarantine(tenant_id,facility_id,patient_id,case_id)
);
CREATE INDEX triage_secure_receipt_time_quarantine ON synqoria_triage_secure_receipts_quarantine (tenant_id,facility_id,committed_at);
CREATE INDEX triage_secure_unpublished_outbox_quarantine ON synqoria_triage_secure_outbox_quarantine (created_at) WHERE published_at IS NULL;
ALTER TABLE synqoria_triage_secure_cases_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_cases_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_secure_cases_quarantine FROM PUBLIC;
ALTER TABLE synqoria_triage_secure_events_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_events_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_secure_events_quarantine FROM PUBLIC;
ALTER TABLE synqoria_triage_secure_outbox_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_outbox_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_secure_outbox_quarantine FROM PUBLIC;
ALTER TABLE synqoria_triage_secure_receipts_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_secure_receipts_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_secure_receipts_quarantine FROM PUBLIC;
-- Intentionally NO CREATE POLICY or GRANT: remains unusable to an application role.
