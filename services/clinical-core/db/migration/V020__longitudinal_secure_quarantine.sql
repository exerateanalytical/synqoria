-- SYNQORIA M04-M06. New clinical case persistence is QUARANTINED by default.
-- No runtime grants, no CREATE POLICY, no BYPASSRLS, no row-security disable statements.
-- DO NOT claim deployable DB integration until a separate restricted-role RLS and transaction test.
CREATE TABLE synqoria_longitudinal_cases_quarantine (
 tenant_id uuid NOT NULL, facility_id uuid NOT NULL, patient_id uuid NOT NULL, encounter_id uuid NOT NULL,
 revision bigint NOT NULL CHECK (revision >= 0 AND revision <= 20000),
 key_id varchar(80) NOT NULL CHECK (key_id ~ '^[A-Za-z0-9._-]{1,80}$'),
 encrypted_snapshot bytea NOT NULL CHECK(octet_length(encrypted_snapshot) BETWEEN 29 AND 4194400),
 audit_head char(64) NOT NULL CHECK (audit_head ~ '^[0-9a-f]{64}$'),
 PRIMARY KEY (tenant_id,facility_id,patient_id,encounter_id)
);
CREATE TABLE synqoria_longitudinal_events_quarantine (
 tenant_id uuid NOT NULL, facility_id uuid NOT NULL, patient_id uuid NOT NULL, encounter_id uuid NOT NULL,
 revision bigint NOT NULL CHECK (revision > 0 AND revision <= 20000),
 command_id uuid NOT NULL, actor_id uuid NOT NULL,
 action varchar(96) NOT NULL, occurred_at timestamptz NOT NULL,
 previous_hash char(64) NOT NULL CHECK (previous_hash ~ '^[0-9a-f]{64}$'),
 state_digest char(64) NOT NULL CHECK (state_digest ~ '^[0-9a-f]{64}$'),
 event_hash char(64) NOT NULL CHECK (event_hash ~ '^[0-9a-f]{64}$'),
 PRIMARY KEY (tenant_id,facility_id,patient_id,encounter_id,revision),
 UNIQUE (tenant_id,facility_id,patient_id,encounter_id,command_id),
 UNIQUE (tenant_id,facility_id,patient_id,encounter_id,command_id,revision,event_hash),
 FOREIGN KEY (tenant_id,facility_id,patient_id,encounter_id)
 REFERENCES synqoria_longitudinal_cases_quarantine (tenant_id,facility_id,patient_id,encounter_id)
);
CREATE TABLE synqoria_longitudinal_receipts_quarantine (
 tenant_id uuid NOT NULL,facility_id uuid NOT NULL,patient_id uuid NOT NULL,encounter_id uuid NOT NULL,
 command_id uuid NOT NULL, revision bigint NOT NULL CHECK (revision > 0),
 fingerprint char(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
 event_hash char(64) NOT NULL CHECK (event_hash ~ '^[0-9a-f]{64}$'),
 PRIMARY KEY (tenant_id,facility_id,patient_id,encounter_id,command_id),
 FOREIGN KEY (tenant_id,facility_id,patient_id,encounter_id,command_id,revision,event_hash)
 REFERENCES synqoria_longitudinal_events_quarantine (tenant_id,facility_id,patient_id,encounter_id,command_id,revision,event_hash)
);
CREATE TABLE synqoria_longitudinal_outbox_quarantine (
 tenant_id uuid NOT NULL,facility_id uuid NOT NULL,patient_id uuid NOT NULL,encounter_id uuid NOT NULL,
 command_id uuid NOT NULL,revision bigint NOT NULL CHECK (revision > 0),
 event_hash char(64) NOT NULL CHECK(event_hash ~ '^[0-9a-f]{64}$'),
 event_type varchar(96) NOT NULL,
 published_at timestamptz,
 PRIMARY KEY (tenant_id,facility_id,patient_id,encounter_id,command_id),
 FOREIGN KEY (tenant_id,facility_id,patient_id,encounter_id,command_id,revision,event_hash)
 REFERENCES synqoria_longitudinal_events_quarantine (tenant_id,facility_id,patient_id,encounter_id,command_id,revision,event_hash)
);
-- Append-only source events/receipts; outbox has a separate review-required dispatcher contract.
CREATE OR REPLACE FUNCTION synqoria_longitudinal_append_only_quarantine() RETURNS trigger
 LANGUAGE plpgsql AS $$BEGIN RAISE EXCEPTION 'immutable longitudinal evidence' USING ERRCODE='42501'; END;$$;
CREATE TRIGGER longitudinal_events_immutable_quarantine BEFORE UPDATE OR DELETE
 ON synqoria_longitudinal_events_quarantine FOR EACH ROW EXECUTE FUNCTION synqoria_longitudinal_append_only_quarantine();
CREATE TRIGGER longitudinal_receipts_immutable_quarantine BEFORE UPDATE OR DELETE
 ON synqoria_longitudinal_receipts_quarantine FOR EACH ROW EXECUTE FUNCTION synqoria_longitudinal_append_only_quarantine();
ALTER TABLE synqoria_longitudinal_cases_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_cases_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_events_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_events_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_receipts_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_receipts_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_outbox_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_outbox_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_longitudinal_cases_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_longitudinal_events_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_longitudinal_receipts_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_longitudinal_outbox_quarantine FROM PUBLIC;
-- Future reviewed migration will install role-bound policies, grants and trustworthy scope attestation.
