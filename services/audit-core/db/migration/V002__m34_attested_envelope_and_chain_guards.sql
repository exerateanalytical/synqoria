-- M34 V002: constrain attested event envelope, stream-head updates and approval scope.
-- External proof publisher MUST bind source actor, clinical subject opaque reference,
-- action, outcome and event-time, not only payload digest.
BEGIN;
ALTER TABLE synqoria_audit.ingest_attestations
 ADD COLUMN source_actor uuid NOT NULL,
 ADD COLUMN subject_ref uuid,
 ADD COLUMN event_action varchar(64) NOT NULL CHECK(event_action ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 ADD COLUMN event_outcome varchar(8) NOT NULL CHECK(event_outcome IN ('SUCCESS','DENIED','ERROR')),
 ADD COLUMN occurred_at timestamptz NOT NULL,
 ADD COLUMN envelope_sha256 char(64) NOT NULL CHECK(envelope_sha256 ~ '^[0-9a-f]{64}$');

CREATE OR REPLACE FUNCTION synqoria_audit.independent_proof_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(OLD.proof_id::text,34));
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'audit_proof_delete_denied' USING ERRCODE='23514';END IF;
 IF ROW(NEW.proof_id,NEW.cell_id,NEW.facility_id,NEW.source_id,NEW.source_event_id,
 NEW.stream_id,NEW.sequence,NEW.payload_sha256,NEW.issuer,NEW.approved_by,
 NEW.valid_from,NEW.created_at,NEW.source_actor,NEW.subject_ref,NEW.event_action,
 NEW.event_outcome,NEW.occurred_at,NEW.envelope_sha256)
 IS DISTINCT FROM ROW(OLD.proof_id,OLD.cell_id,OLD.facility_id,OLD.source_id,OLD.source_event_id,
 OLD.stream_id,OLD.sequence,OLD.payload_sha256,OLD.issuer,OLD.approved_by,
 OLD.valid_from,OLD.created_at,OLD.source_actor,OLD.subject_ref,OLD.event_action,
 OLD.event_outcome,OLD.occurred_at,OLD.envelope_sha256)
 OR NEW.valid_until>OLD.valid_until
 OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN
  RAISE EXCEPTION 'audit_proof_rebinding_denied' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;

CREATE FUNCTION synqoria_audit.stream_head_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
DECLARE stored_hash text;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'audit_stream_head_delete_denied' USING ERRCODE='23514';END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.last_sequence<>0 OR NEW.last_hash<>repeat('0',64) THEN
   RAISE EXCEPTION 'audit_stream_initial_state_invalid' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
 END IF;
 IF ROW(NEW.cell_id,NEW.facility_id,NEW.stream_id) IS DISTINCT FROM
    ROW(OLD.cell_id,OLD.facility_id,OLD.stream_id)
    OR NEW.last_sequence<>OLD.last_sequence+1 THEN
  RAISE EXCEPTION 'audit_stream_sequence_invalid' USING ERRCODE='23514';
 END IF;
 SELECT event_sha256 INTO stored_hash FROM synqoria_audit.evidence_events
 WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND stream_id=NEW.stream_id
   AND sequence=NEW.last_sequence;
 IF stored_hash IS NULL OR stored_hash<>NEW.last_hash THEN
  RAISE EXCEPTION 'audit_stream_head_without_matching_event' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m34_stream_head_guard BEFORE INSERT OR UPDATE OR DELETE ON synqoria_audit.stream_heads
 FOR EACH ROW EXECUTE FUNCTION synqoria_audit.stream_head_guard();
REVOKE ALL ON FUNCTION synqoria_audit.stream_head_guard() FROM PUBLIC;

-- A reviewed retention/export scope cannot be replaced during decision.
-- Replace generic review trigger with schema-specific functions:
-- PostgreSQL PL/pgSQL may still resolve referenced fields in an unused branch.
DROP TRIGGER m34_export_review_guard ON synqoria_audit.export_requests;
DROP TRIGGER m34_retention_review_guard ON synqoria_audit.retention_reviews;
DROP FUNCTION synqoria_audit.review_guard();

CREATE FUNCTION synqoria_audit.export_review_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'audit_review_delete_denied' USING ERRCODE='23514';END IF;
 IF NEW.version<>OLD.version+1 OR OLD.status<>'PROPOSED'
  OR NEW.status NOT IN('APPROVED','DENIED') OR
  ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.stream_id,NEW.requested_by,NEW.created_at,
      NEW.purpose,NEW.recipient,NEW.from_sequence,NEW.to_sequence,NEW.expires_at)
  IS DISTINCT FROM
  ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.stream_id,OLD.requested_by,OLD.created_at,
      OLD.purpose,OLD.recipient,OLD.from_sequence,OLD.to_sequence,OLD.expires_at)
 THEN RAISE EXCEPTION 'audit_export_scope_immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE FUNCTION synqoria_audit.retention_review_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'audit_review_delete_denied' USING ERRCODE='23514';END IF;
 IF NEW.version<>OLD.version+1 OR OLD.status<>'PROPOSED'
  OR NEW.status NOT IN('APPROVED','DENIED') OR
  ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.stream_id,NEW.requested_by,NEW.created_at,
      NEW.policy,NEW.reason)
  IS DISTINCT FROM
  ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.stream_id,OLD.requested_by,OLD.created_at,
      OLD.policy,OLD.reason)
 THEN RAISE EXCEPTION 'audit_retention_scope_immutable' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m34_export_review_guard BEFORE UPDATE OR DELETE ON synqoria_audit.export_requests
 FOR EACH ROW EXECUTE FUNCTION synqoria_audit.export_review_guard();
CREATE TRIGGER m34_retention_review_guard BEFORE UPDATE OR DELETE ON synqoria_audit.retention_reviews
 FOR EACH ROW EXECUTE FUNCTION synqoria_audit.retention_review_guard();
REVOKE ALL ON FUNCTION synqoria_audit.export_review_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_audit.retention_review_guard() FROM PUBLIC;
COMMIT;
