-- SYNQORIA M27 | mortality registry & custody. Apply after V004 as migration owner.
-- No runtime DDL, case deletion, forged identity/authority evidence or custody rewrite.
-- All references are to validated hospital records; no free-text decedent names or death narratives.
BEGIN;
CREATE TABLE synqoria_operations.mortuary_cases (
 case_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 decedent_ref uuid NOT NULL,
 intake_ref varchar(160) NOT NULL,
 death_ref varchar(160) NOT NULL,
 state varchar(32) NOT NULL CHECK(state IN
  ('RECEIVED','IDENTIFIED','STORED','RELEASE_REVIEWED','RELEASE_AUTHORIZED','RELEASED')),
 version bigint NOT NULL CHECK(version>=1),
 current_storage varchar(160),
 intake_actor uuid NOT NULL,
 last_actor uuid NOT NULL,
 received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 released_at timestamptz,
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,case_id),
 UNIQUE(cell_id,facility_id,decedent_ref),
 CHECK((state IN('STORED','RELEASE_REVIEWED','RELEASE_AUTHORIZED') AND current_storage IS NOT NULL)
  OR (state IN('RECEIVED','IDENTIFIED','RELEASED') AND current_storage IS NULL)),
 CHECK((state='RELEASED') = (released_at IS NOT NULL)),
 CHECK(intake_ref<>'' AND death_ref<>'')
);
CREATE INDEX mortuary_worklist ON synqoria_operations.mortuary_cases
(cell_id,facility_id,state,updated_at DESC,case_id);

-- External evidence issuer is independently administered. Runtime can SELECT but never
-- create/alter the legal release, verified death, identity, custody, or recipient proof.
CREATE TABLE synqoria_operations.mortuary_authority_evidence (
 proof_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 decedent_ref uuid NOT NULL,
 case_id uuid,
 for_action varchar(32) NOT NULL CHECK(for_action IN
 ('RECEIVE','IDENTIFY','STORE','TRANSFER','REVIEW_RELEASE',
  'AUTHORIZE_RELEASE','HANDOVER')),
 expected_version bigint NOT NULL CHECK(expected_version>=0),
 proof_kind varchar(32) NOT NULL CHECK(proof_kind IN
 ('DEATH_CONFIRMED','IDENTITY_ATTESTED','IDENTITY_VERIFIED','STORAGE_SAFE',
  'CUSTODY_ACCEPTED','LEGAL_RELEASE','RECIPIENT_VERIFIED',
  'RELEASE_AUTHORIZATION','HANDOVER_RECEIPT')),
 external_ref varchar(160) NOT NULL,
 authority_issuer varchar(160) NOT NULL,
 approved_by uuid NOT NULL,
 valid_from timestamptz NOT NULL,
 valid_until timestamptz NOT NULL,
 revoked_at timestamptz,
 digest_sha256 char(64) NOT NULL CHECK(digest_sha256 ~ '^[a-f0-9]{64}$'),
 issued_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,case_id)
  REFERENCES synqoria_operations.mortuary_cases(cell_id,facility_id,case_id),
 CHECK(valid_until>valid_from AND external_ref<>'' AND authority_issuer<>''),
 CHECK((case_id IS NULL AND expected_version=0 AND for_action='RECEIVE') OR
       (case_id IS NOT NULL AND expected_version>=1 AND for_action<>'RECEIVE'))
);
CREATE INDEX mortuary_valid_evidence ON synqoria_operations.mortuary_authority_evidence
 (cell_id,facility_id,decedent_ref,case_id,for_action,expected_version,proof_kind,external_ref)
 WHERE revoked_at IS NULL;

CREATE TABLE synqoria_operations.mortuary_legal_holds (
 hold_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 case_id uuid NOT NULL,
 authority_ref varchar(160) NOT NULL,
 reason_code varchar(40) NOT NULL,
 ordered_by uuid NOT NULL,
 active boolean NOT NULL DEFAULT true,
 ordered_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 rescinded_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,case_id)
  REFERENCES synqoria_operations.mortuary_cases(cell_id,facility_id,case_id),
 CHECK(authority_ref<>'' AND reason_code<>''),
 CHECK((active AND rescinded_at IS NULL) OR (NOT active AND rescinded_at IS NOT NULL))
);
CREATE INDEX mortuary_active_hold ON synqoria_operations.mortuary_legal_holds
 (cell_id,facility_id,case_id) WHERE active;

CREATE TABLE synqoria_operations.mortuary_custody_events (
 event_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 case_id uuid NOT NULL,
 case_version bigint NOT NULL CHECK(case_version>=1),
 event_type varchar(24) NOT NULL CHECK(event_type IN ('INTAKE','STORE','TRANSFER','HANDOVER')),
 from_storage varchar(160),
 to_storage varchar(160),
 actor_id uuid NOT NULL,
 receiving_staff_id uuid,
 evidence_ref varchar(160) NOT NULL,
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,case_id)
  REFERENCES synqoria_operations.mortuary_cases(cell_id,facility_id,case_id),
 UNIQUE(cell_id,facility_id,case_id,case_version),
 CHECK(evidence_ref<>''),
 CHECK((event_type IN ('STORE','TRANSFER') AND to_storage IS NOT NULL AND receiving_staff_id IS NOT NULL)
  OR (event_type IN ('INTAKE','HANDOVER') AND to_storage IS NULL)),
 CHECK(event_type<>'TRANSFER' OR
  (from_storage IS NOT NULL AND from_storage<>to_storage AND receiving_staff_id<>actor_id))
);
CREATE INDEX mortuary_custody_chain ON synqoria_operations.mortuary_custody_events
 (cell_id,facility_id,case_id,case_version DESC);

CREATE TABLE synqoria_operations.mortuary_release_reviews (
 approval_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 case_id uuid NOT NULL,
 stage varchar(12) NOT NULL CHECK(stage IN ('REVIEW','AUTHORIZE')),
 actor_id uuid NOT NULL,
 authority_ref varchar(160) NOT NULL,
 recipient_ref varchar(160) NOT NULL,
 case_version bigint NOT NULL CHECK(case_version>=1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,case_id)
  REFERENCES synqoria_operations.mortuary_cases(cell_id,facility_id,case_id),
 UNIQUE(cell_id,facility_id,case_id,stage),
 UNIQUE(cell_id,facility_id,case_id,actor_id),
 CHECK(authority_ref<>'' AND recipient_ref<>'')
);

CREATE TABLE synqoria_operations.mortuary_commands (
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 request_sha256 char(64) NOT NULL CHECK(request_sha256 ~ '^[a-f0-9]{64}$'),
 case_id uuid,
 result_version bigint CHECK(result_version>=1),
 recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id),
 FOREIGN KEY(cell_id,facility_id,case_id)
  REFERENCES synqoria_operations.mortuary_cases(cell_id,facility_id,case_id),
 CHECK((case_id IS NULL)=(result_version IS NULL))
);
CREATE TABLE synqoria_operations.mortuary_audit_events (
 event_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 case_id uuid NOT NULL,
 case_version bigint NOT NULL CHECK(case_version>=1),
 action varchar(32) NOT NULL,
 from_state varchar(32),
 to_state varchar(32) NOT NULL,
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 evidence_hash char(64) NOT NULL CHECK(evidence_hash ~ '^[a-f0-9]{64}$'),
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,case_id)
  REFERENCES synqoria_operations.mortuary_cases(cell_id,facility_id,case_id),
 UNIQUE(cell_id,facility_id,case_id,case_version)
);
CREATE TABLE synqoria_operations.mortuary_outbox (
 event_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 case_id uuid NOT NULL,
 case_version bigint NOT NULL,
 event_type varchar(32) NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id)
  REFERENCES synqoria_operations.mortuary_audit_events(cell_id,facility_id,event_id),
 CHECK(NOT(payload ? 'name') AND NOT(payload ? 'death_ref'))
);
ALTER TABLE synqoria_operations.mortuary_audit_events
 ADD UNIQUE(cell_id,facility_id,event_id);
CREATE INDEX mortuary_pending_events ON synqoria_operations.mortuary_outbox
(created_at,event_id) WHERE published_at IS NULL;

-- Always-on tenant/facility isolation, even when queried by table owner.
DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY[
  'mortuary_cases','mortuary_authority_evidence','mortuary_legal_holds',
  'mortuary_custody_events','mortuary_release_reviews','mortuary_commands',
  'mortuary_audit_events','mortuary_outbox']
 LOOP
  EXECUTE format('ALTER TABLE synqoria_operations.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_operations.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('REVOKE ALL ON synqoria_operations.%I FROM PUBLIC',rel);
  EXECUTE format(
   'CREATE POLICY mortuary_cell_facility ON synqoria_operations.%I FOR ALL ' ||
   'TO synqoria_operations_runtime ' ||
   'USING (cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) ' ||
   'WITH CHECK (cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.mortuary_cases TO synqoria_operations_runtime;
GRANT SELECT ON synqoria_operations.mortuary_authority_evidence TO synqoria_operations_runtime;
GRANT SELECT ON synqoria_operations.mortuary_legal_holds TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.mortuary_custody_events TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.mortuary_release_reviews TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.mortuary_commands TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.mortuary_audit_events TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.mortuary_outbox TO synqoria_operations_runtime;
COMMIT;
