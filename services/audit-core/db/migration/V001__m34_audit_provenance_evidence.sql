-- M34 independent nonclinical audit/provenance records.
-- External issuers supply signed-authority attestations out of band; runtime may only READ them.
-- This schema records metadata/digests, never clinical narrative or original PHI documents.
BEGIN;
DO $guard$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_audit_runtime'
  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication)
 THEN RAISE EXCEPTION 'missing_restricted_audit_runtime_role'; END IF;
END $guard$;
CREATE SCHEMA synqoria_audit;
REVOKE ALL ON SCHEMA synqoria_audit FROM PUBLIC;

CREATE TABLE synqoria_audit.ingest_attestations(
 proof_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 source_id uuid NOT NULL,source_event_id uuid NOT NULL,stream_id uuid NOT NULL,
 sequence bigint NOT NULL CHECK(sequence>=1),payload_sha256 char(64) NOT NULL
 CHECK(payload_sha256 ~ '^[0-9a-f]{64}$'),
 issuer varchar(120) NOT NULL,approved_by uuid NOT NULL,
 valid_from timestamptz NOT NULL,valid_until timestamptz NOT NULL,
 revoked_at timestamptz,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,proof_id),
 CHECK(valid_until>valid_from)
);
CREATE TABLE synqoria_audit.stream_heads(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,stream_id uuid NOT NULL,
 last_sequence bigint NOT NULL DEFAULT 0 CHECK(last_sequence>=0),
 last_hash char(64) NOT NULL DEFAULT repeat('0',64)
 CHECK(last_hash ~ '^[0-9a-f]{64}$'),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,stream_id)
);
CREATE TABLE synqoria_audit.evidence_events(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,stream_id uuid NOT NULL,
 source_id uuid NOT NULL,source_event_id uuid NOT NULL,source_actor uuid NOT NULL,
 subject_ref uuid,sequence bigint NOT NULL CHECK(sequence>=1),
 action varchar(64) NOT NULL, outcome varchar(8) NOT NULL CHECK(outcome IN('SUCCESS','DENIED','ERROR')),
 payload_sha256 char(64) NOT NULL CHECK(payload_sha256 ~ '^[0-9a-f]{64}$'),
 previous_sha256 char(64) NOT NULL CHECK(previous_sha256 ~ '^[0-9a-f]{64}$'),
 event_sha256 char(64) NOT NULL CHECK(event_sha256 ~ '^[0-9a-f]{64}$'),
 proof_id uuid NOT NULL, occurred_at timestamptz NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,stream_id,sequence),
 UNIQUE(cell_id,facility_id,source_id,source_event_id),
 FOREIGN KEY(cell_id,facility_id,proof_id) REFERENCES synqoria_audit.ingest_attestations(cell_id,facility_id,proof_id)
);
CREATE INDEX m34_events_subject ON synqoria_audit.evidence_events
 (cell_id,facility_id,subject_ref,occurred_at DESC,id);
CREATE INDEX m34_events_stream ON synqoria_audit.evidence_events
 (cell_id,facility_id,stream_id,sequence DESC);
CREATE TABLE synqoria_audit.export_requests(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 stream_id uuid NOT NULL,purpose varchar(64) NOT NULL,recipient varchar(64) NOT NULL,
 from_sequence bigint NOT NULL,to_sequence bigint NOT NULL,
 expires_at timestamptz NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'PROPOSED' CHECK(status IN('PROPOSED','APPROVED','DENIED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 requested_by uuid NOT NULL,reviewed_by uuid,reviewed_at timestamptz,decision_reason varchar(64),
 manifest_sha256 char(64) CHECK(manifest_sha256 ~ '^[0-9a-f]{64}$'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 CHECK(from_sequence>=1 AND to_sequence>=from_sequence AND to_sequence-from_sequence<=10000),
 CHECK((status='PROPOSED')=(reviewed_by IS NULL AND reviewed_at IS NULL)),
 CHECK(reviewed_by IS NULL OR reviewed_by<>requested_by),
 CHECK(status<>'APPROVED' OR manifest_sha256 IS NOT NULL)
);
CREATE INDEX m34_exports_scope ON synqoria_audit.export_requests
 (cell_id,facility_id,status,created_at DESC,id);
CREATE TABLE synqoria_audit.retention_holds(
 hold_id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 stream_id uuid NOT NULL,reason varchar(64) NOT NULL,
 authority_ref varchar(160) NOT NULL,ordered_by uuid NOT NULL,
 issued_at timestamptz NOT NULL DEFAULT clock_timestamp(),rescinded_at timestamptz,
 UNIQUE(cell_id,facility_id,hold_id)
);
CREATE INDEX m34_holds_active ON synqoria_audit.retention_holds
 (cell_id,facility_id,stream_id,issued_at DESC) WHERE rescinded_at IS NULL;
CREATE TABLE synqoria_audit.retention_reviews(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,stream_id uuid NOT NULL,
 policy varchar(64) NOT NULL,reason varchar(64) NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'PROPOSED' CHECK(status IN('PROPOSED','APPROVED','DENIED')),
 requested_by uuid NOT NULL,reviewed_by uuid,reviewed_at timestamptz,
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 CHECK((status='PROPOSED')=(reviewed_by IS NULL AND reviewed_at IS NULL)),
 CHECK(reviewed_by IS NULL OR reviewed_by<>requested_by)
);
CREATE TABLE synqoria_audit.commands(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,actor_id uuid NOT NULL,
 command_id uuid NOT NULL,request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 response jsonb,http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id),
 CHECK((response IS NULL)=(http_status IS NULL))
);
CREATE TABLE synqoria_audit.audit_actions(
 event_id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 aggregate_type varchar(24) NOT NULL,aggregate_id uuid NOT NULL,
 action varchar(48) NOT NULL,version bigint NOT NULL CHECK(version>=1),
 actor_id uuid NOT NULL,command_id uuid NOT NULL,
 digest_sha256 char(64) NOT NULL CHECK(digest_sha256 ~ '^[0-9a-f]{64}$'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id)
);
CREATE TABLE synqoria_audit.outbox(
 event_id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 event_type varchar(72) NOT NULL,aggregate_id uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_audit.audit_actions(cell_id,facility_id,event_id)
);

CREATE FUNCTION synqoria_audit.append_only() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN RAISE EXCEPTION 'audit_evidence_is_append_only' USING ERRCODE='23514';END $f$;
DO $trig$ DECLARE rel text; BEGIN
 FOREACH rel IN ARRAY ARRAY['evidence_events','audit_actions','outbox'] LOOP
  EXECUTE format('CREATE TRIGGER %I_immutable BEFORE UPDATE OR DELETE ON synqoria_audit.%I FOR EACH ROW EXECUTE FUNCTION synqoria_audit.append_only()',rel,rel);
  EXECUTE format('CREATE TRIGGER %I_no_truncate BEFORE TRUNCATE ON synqoria_audit.%I FOR EACH STATEMENT EXECUTE FUNCTION synqoria_audit.append_only()',rel,rel);
 END LOOP;
END $trig$;

CREATE FUNCTION synqoria_audit.independent_proof_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(OLD.proof_id::text,34));
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'audit_proof_delete_denied' USING ERRCODE='23514';END IF;
 IF ROW(NEW.proof_id,NEW.cell_id,NEW.facility_id,NEW.source_id,NEW.source_event_id,
 NEW.stream_id,NEW.sequence,NEW.payload_sha256,NEW.issuer,NEW.approved_by,
 NEW.valid_from,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.proof_id,OLD.cell_id,OLD.facility_id,OLD.source_id,OLD.source_event_id,
 OLD.stream_id,OLD.sequence,OLD.payload_sha256,OLD.issuer,OLD.approved_by,
 OLD.valid_from,OLD.created_at)
 OR NEW.valid_until>OLD.valid_until
 OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN
  RAISE EXCEPTION 'audit_proof_rebinding_denied' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m34_ingest_proof_guard BEFORE UPDATE OR DELETE ON synqoria_audit.ingest_attestations
 FOR EACH ROW EXECUTE FUNCTION synqoria_audit.independent_proof_guard();

CREATE FUNCTION synqoria_audit.hold_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'audit_hold_delete_denied' USING ERRCODE='23514';END IF;
 IF ROW(NEW.hold_id,NEW.cell_id,NEW.facility_id,NEW.stream_id,NEW.reason,
  NEW.authority_ref,NEW.ordered_by,NEW.issued_at)
 IS DISTINCT FROM ROW(OLD.hold_id,OLD.cell_id,OLD.facility_id,OLD.stream_id,OLD.reason,
  OLD.authority_ref,OLD.ordered_by,OLD.issued_at)
 OR (OLD.rescinded_at IS NOT NULL AND NEW.rescinded_at IS DISTINCT FROM OLD.rescinded_at) THEN
  RAISE EXCEPTION 'audit_hold_rebinding_denied' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m34_hold_guard BEFORE UPDATE OR DELETE ON synqoria_audit.retention_holds
 FOR EACH ROW EXECUTE FUNCTION synqoria_audit.hold_guard();

CREATE FUNCTION synqoria_audit.review_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'audit_review_delete_denied' USING ERRCODE='23514';END IF;
 IF NEW.version<>OLD.version+1 OR OLD.status<>'PROPOSED' OR
  NEW.status NOT IN('APPROVED','DENIED')
  OR ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.stream_id,NEW.requested_by,NEW.created_at)
  IS DISTINCT FROM ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.stream_id,OLD.requested_by,OLD.created_at)
 THEN RAISE EXCEPTION 'audit_review_transition_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m34_export_review_guard BEFORE UPDATE OR DELETE ON synqoria_audit.export_requests
 FOR EACH ROW EXECUTE FUNCTION synqoria_audit.review_guard();
CREATE TRIGGER m34_retention_review_guard BEFORE UPDATE OR DELETE ON synqoria_audit.retention_reviews
 FOR EACH ROW EXECUTE FUNCTION synqoria_audit.review_guard();

DO $policy$ DECLARE rel text; BEGIN
 FOREACH rel IN ARRAY ARRAY['ingest_attestations','stream_heads','evidence_events','export_requests',
  'retention_holds','retention_reviews','commands','audit_actions','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_audit.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_audit.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('CREATE POLICY m34_cell_boundary ON synqoria_audit.%I USING
   (cell_id::text=current_setting(''synqoria.cell'',true)
    AND facility_id::text=current_setting(''synqoria.facility'',true))
   WITH CHECK (cell_id::text=current_setting(''synqoria.cell'',true)
    AND facility_id::text=current_setting(''synqoria.facility'',true))',rel);
 END LOOP;
END $policy$;

GRANT USAGE ON SCHEMA synqoria_audit TO synqoria_audit_runtime;
GRANT SELECT ON ALL TABLES IN SCHEMA synqoria_audit TO synqoria_audit_runtime;
GRANT INSERT,UPDATE ON synqoria_audit.stream_heads,synqoria_audit.export_requests,
 synqoria_audit.retention_reviews,synqoria_audit.commands TO synqoria_audit_runtime;
GRANT INSERT ON synqoria_audit.evidence_events,synqoria_audit.audit_actions,
 synqoria_audit.outbox TO synqoria_audit_runtime;
REVOKE ALL ON FUNCTION synqoria_audit.append_only() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_audit.independent_proof_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_audit.hold_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_audit.review_guard() FROM PUBLIC;
COMMIT;
