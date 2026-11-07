-- M32: hospital-cell independent privacy/consent authority boundary.
-- Applied by migration admin. Never apply from a runtime owner or a hospital-cell API token.
BEGIN;
DO $guard$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_privacy_runtime'
  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication)
 THEN RAISE EXCEPTION 'missing_restricted_privacy_runtime_role'; END IF;
END $guard$;
CREATE SCHEMA synqoria_privacy;
REVOKE ALL ON SCHEMA synqoria_privacy FROM PUBLIC;
CREATE TABLE synqoria_privacy.authority_evidence(
 proof_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,subject_ref uuid NOT NULL,
 purpose varchar(64) NOT NULL,recipient varchar(64) NOT NULL,operation varchar(64) NOT NULL,
 source_ref varchar(160) NOT NULL,issuer varchar(120) NOT NULL,approved_by uuid NOT NULL,
 valid_from timestamptz NOT NULL,valid_until timestamptz NOT NULL,
 digest_sha256 char(64) NOT NULL CHECK(digest_sha256 ~ '^[0-9a-f]{64}$'),
 revoked_at timestamptz,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,proof_id),
 CHECK(valid_until>valid_from),
 CHECK(purpose ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 CHECK(recipient ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 CHECK(operation ~ '^[A-Z][A-Z0-9_]{1,63}$')
);
CREATE TABLE synqoria_privacy.directives(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 subject_ref uuid NOT NULL,purpose varchar(64) NOT NULL,recipient varchar(64) NOT NULL,
 operation varchar(64) NOT NULL,effective_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,status varchar(12) NOT NULL DEFAULT 'DRAFT'
  CHECK(status IN ('DRAFT','VERIFIED','ACTIVE','REVOKED')),
 creator_id uuid NOT NULL,verified_by uuid,activated_by uuid,revoked_by uuid,
 proof_id uuid,version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 revoked_at timestamptz,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,proof_id) REFERENCES synqoria_privacy.authority_evidence(cell_id,facility_id,proof_id),
 CHECK(expires_at>effective_at),
 CHECK(purpose ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 CHECK(recipient ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 CHECK(operation ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 CHECK(verified_by IS NULL OR verified_by<>creator_id),
 CHECK(activated_by IS NULL OR (activated_by<>verified_by AND activated_by<>creator_id)),
 CHECK((status='DRAFT')=(verified_by IS NULL AND proof_id IS NULL)),
 CHECK(status NOT IN ('VERIFIED','ACTIVE','REVOKED') OR verified_by IS NOT NULL),
 CHECK(status NOT IN ('ACTIVE','REVOKED') OR activated_by IS NOT NULL),
 CHECK((status='REVOKED')=(revoked_at IS NOT NULL AND revoked_by IS NOT NULL))
);
CREATE INDEX m32_directive_subject ON synqoria_privacy.directives
 (cell_id,facility_id,subject_ref,status,updated_at DESC,id);
CREATE TABLE synqoria_privacy.disclosures(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 subject_ref uuid NOT NULL,directive_id uuid NOT NULL,
 purpose varchar(64) NOT NULL,recipient varchar(64) NOT NULL,operation varchar(64) NOT NULL,
 record_class varchar(64) NOT NULL,status varchar(12) NOT NULL DEFAULT 'PENDING'
 CHECK(status IN ('PENDING','AUTHORIZED','DENIED')),
 requester_id uuid NOT NULL,reviewer_id uuid,reason varchar(64),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 reviewed_at timestamptz,
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,directive_id) REFERENCES synqoria_privacy.directives(cell_id,facility_id,id),
 CHECK(reviewer_id IS NULL OR reviewer_id<>requester_id),
 CHECK((status='PENDING')=(reviewer_id IS NULL AND reviewed_at IS NULL)),
 CHECK(status<>'AUTHORIZED' OR reason='AUTHORIZED'),
 CHECK(status<>'DENIED' OR reason IS NOT NULL)
);
CREATE INDEX m32_disclosure_pending ON synqoria_privacy.disclosures
 (cell_id,facility_id,status,created_at DESC,id);
CREATE TABLE synqoria_privacy.privacy_cases(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 subject_ref uuid NOT NULL,category varchar(16) NOT NULL
 CHECK(category IN ('ACCESS','CORRECTION','COMPLAINT','INCIDENT')),
 reason varchar(64) NOT NULL,status varchar(16) NOT NULL DEFAULT 'OPEN'
 CHECK(status IN ('OPEN','CLOSED')),
 opened_by uuid NOT NULL,closed_by uuid,close_reason varchar(64),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),closed_at timestamptz,
 UNIQUE(cell_id,facility_id,id),
 CHECK((status='CLOSED')=(closed_by IS NOT NULL AND close_reason IS NOT NULL AND closed_at IS NOT NULL)),
 CHECK(closed_by IS NULL OR closed_by<>opened_by)
);
CREATE INDEX m32_privacy_cases ON synqoria_privacy.privacy_cases
 (cell_id,facility_id,status,created_at DESC,id);
CREATE TABLE synqoria_privacy.commands(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,actor_id uuid NOT NULL,
 command_id uuid NOT NULL,request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 response jsonb,http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id),
 CHECK((response IS NULL)=(http_status IS NULL))
);
CREATE TABLE synqoria_privacy.audit_events(
 event_id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 aggregate_type varchar(24) NOT NULL,aggregate_id uuid NOT NULL,
 action varchar(48) NOT NULL,aggregate_version bigint NOT NULL,
 actor_id uuid NOT NULL,command_id uuid NOT NULL,
 digest_sha256 char(64) NOT NULL CHECK(digest_sha256 ~ '^[0-9a-f]{64}$'),
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id)
);
CREATE TABLE synqoria_privacy.outbox(
 event_id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 aggregate_id uuid NOT NULL,event_type varchar(80) NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_privacy.audit_events(cell_id,facility_id,event_id)
);
CREATE FUNCTION synqoria_privacy.immutable_audit() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN RAISE EXCEPTION 'privacy_audit_immutable' USING ERRCODE='23514';END $f$;
CREATE TRIGGER m32_audit_no_rewrite BEFORE UPDATE OR DELETE ON synqoria_privacy.audit_events
 FOR EACH ROW EXECUTE FUNCTION synqoria_privacy.immutable_audit();
CREATE TRIGGER m32_audit_no_truncate BEFORE TRUNCATE ON synqoria_privacy.audit_events
 FOR EACH STATEMENT EXECUTE FUNCTION synqoria_privacy.immutable_audit();
CREATE FUNCTION synqoria_privacy.proof_immutable() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'privacy_evidence_not_deletable' USING ERRCODE='23514';END IF;
 IF ROW(NEW.proof_id,NEW.cell_id,NEW.facility_id,NEW.subject_ref,NEW.purpose,NEW.recipient,
  NEW.operation,NEW.source_ref,NEW.issuer,NEW.approved_by,NEW.valid_from,NEW.digest_sha256,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.proof_id,OLD.cell_id,OLD.facility_id,OLD.subject_ref,OLD.purpose,OLD.recipient,
  OLD.operation,OLD.source_ref,OLD.issuer,OLD.approved_by,OLD.valid_from,OLD.digest_sha256,OLD.created_at)
 OR NEW.valid_until>OLD.valid_until OR (OLD.revoked_at IS NOT NULL AND
 NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN
  RAISE EXCEPTION 'privacy_evidence_rebinding_denied' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m32_proof_guard BEFORE UPDATE OR DELETE ON synqoria_privacy.authority_evidence
 FOR EACH ROW EXECUTE FUNCTION synqoria_privacy.proof_immutable();
CREATE FUNCTION synqoria_privacy.directive_transition_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'privacy_directive_no_delete' USING ERRCODE='23514';END IF;
 IF ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.subject_ref,NEW.purpose,
  NEW.recipient,NEW.operation,NEW.effective_at,NEW.expires_at,NEW.creator_id,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.subject_ref,OLD.purpose,
  OLD.recipient,OLD.operation,OLD.effective_at,OLD.expires_at,OLD.creator_id,OLD.created_at)
 OR NEW.version<>OLD.version+1 OR
 NOT ((OLD.status='DRAFT' AND NEW.status='VERIFIED') OR
      (OLD.status='VERIFIED' AND NEW.status='ACTIVE') OR
      (OLD.status='ACTIVE' AND NEW.status='REVOKED'))
 OR (OLD.verified_by IS NOT NULL AND NEW.verified_by IS DISTINCT FROM OLD.verified_by)
 OR (OLD.activated_by IS NOT NULL AND NEW.activated_by IS DISTINCT FROM OLD.activated_by)
 OR (OLD.proof_id IS NOT NULL AND NEW.proof_id IS DISTINCT FROM OLD.proof_id) THEN
  RAISE EXCEPTION 'privacy_directive_transition_denied' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m32_directive_guard BEFORE UPDATE OR DELETE ON synqoria_privacy.directives
 FOR EACH ROW EXECUTE FUNCTION synqoria_privacy.directive_transition_guard();
CREATE FUNCTION synqoria_privacy.disclosure_transition_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'privacy_disclosure_no_delete' USING ERRCODE='23514';END IF;
 IF OLD.status<>'PENDING' OR NEW.status NOT IN('AUTHORIZED','DENIED') OR
 NEW.version<>OLD.version+1 OR
 ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.subject_ref,NEW.directive_id,NEW.purpose,
 NEW.recipient,NEW.operation,NEW.record_class,NEW.requester_id,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.subject_ref,OLD.directive_id,OLD.purpose,
 OLD.recipient,OLD.operation,OLD.record_class,OLD.requester_id,OLD.created_at) THEN
  RAISE EXCEPTION 'privacy_disclosure_transition_denied' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m32_disclosure_guard BEFORE UPDATE OR DELETE ON synqoria_privacy.disclosures
 FOR EACH ROW EXECUTE FUNCTION synqoria_privacy.disclosure_transition_guard();
CREATE FUNCTION synqoria_privacy.case_transition_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'privacy_case_no_delete' USING ERRCODE='23514';END IF;
 IF OLD.status<>'OPEN' OR NEW.status<>'CLOSED' OR NEW.version<>OLD.version+1 OR
 ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.subject_ref,NEW.category,NEW.reason,NEW.opened_by,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.subject_ref,OLD.category,OLD.reason,OLD.opened_by,OLD.created_at)
 THEN RAISE EXCEPTION 'privacy_case_transition_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m32_case_guard BEFORE UPDATE OR DELETE ON synqoria_privacy.privacy_cases
 FOR EACH ROW EXECUTE FUNCTION synqoria_privacy.case_transition_guard();

DO $policy$ DECLARE name text; BEGIN
 FOREACH name IN ARRAY ARRAY['authority_evidence','directives','disclosures',
   'privacy_cases','commands','audit_events','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_privacy.%I ENABLE ROW LEVEL SECURITY',name);
  EXECUTE format('ALTER TABLE synqoria_privacy.%I FORCE ROW LEVEL SECURITY',name);
  EXECUTE format('CREATE POLICY cell_facility_boundary ON synqoria_privacy.%I USING
   (cell_id::text=current_setting(''synqoria.cell'',true) AND
    facility_id::text=current_setting(''synqoria.facility'',true))
   WITH CHECK (cell_id::text=current_setting(''synqoria.cell'',true) AND
    facility_id::text=current_setting(''synqoria.facility'',true))',name);
 END LOOP;
END $policy$;
GRANT USAGE ON SCHEMA synqoria_privacy TO synqoria_privacy_runtime;
GRANT SELECT ON ALL TABLES IN SCHEMA synqoria_privacy TO synqoria_privacy_runtime;
GRANT INSERT,UPDATE ON synqoria_privacy.directives,synqoria_privacy.disclosures,
 synqoria_privacy.privacy_cases,synqoria_privacy.commands TO synqoria_privacy_runtime;
GRANT INSERT ON synqoria_privacy.audit_events,synqoria_privacy.outbox TO synqoria_privacy_runtime;
REVOKE ALL ON FUNCTION synqoria_privacy.immutable_audit() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_privacy.proof_immutable() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_privacy.directive_transition_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_privacy.disclosure_transition_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_privacy.case_transition_guard() FROM PUBLIC;
COMMIT;
