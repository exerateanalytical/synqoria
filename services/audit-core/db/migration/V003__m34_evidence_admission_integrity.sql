-- M34 V003: reject a forged event even when a caller bypasses the Java domain API.
-- The externally attested exact envelope is checked under the issuer revocation fence,
-- then the SQL event digest is recalculated against the locked previous stream head.
-- pgcrypto is a supported built-in PostgreSQL extension provisioned by the migrator.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE FUNCTION synqoria_audit.evidence_admission_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $guard$
DECLARE p record; h record; expected_hash text;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(NEW.proof_id::text,34));
 SELECT source_id,source_event_id,stream_id,sequence,payload_sha256,
        source_actor,subject_ref,event_action,event_outcome,occurred_at,
        envelope_sha256,approved_by,revoked_at,valid_from,valid_until
 INTO p FROM synqoria_audit.ingest_attestations
 WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND proof_id=NEW.proof_id;
 IF NOT FOUND OR p.revoked_at IS NOT NULL OR
     p.valid_from>clock_timestamp() OR p.valid_until<=clock_timestamp()
     OR p.approved_by=NEW.source_actor OR
     ROW(NEW.source_id,NEW.source_event_id,NEW.stream_id,NEW.sequence,
         NEW.payload_sha256,NEW.source_actor,NEW.subject_ref,NEW.action,NEW.outcome,
         NEW.occurred_at)
     IS DISTINCT FROM
     ROW(p.source_id,p.source_event_id,p.stream_id,p.sequence,
         p.payload_sha256,p.source_actor,p.subject_ref,p.event_action,p.event_outcome,
         p.occurred_at) THEN
   RAISE EXCEPTION 'audit_event_independent_attestation_required' USING ERRCODE='23514';
 END IF;
 SELECT last_sequence,last_hash INTO h FROM synqoria_audit.stream_heads
 WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND stream_id=NEW.stream_id
 FOR UPDATE;
 IF NOT FOUND OR NEW.sequence<>h.last_sequence+1 OR NEW.previous_sha256<>h.last_hash THEN
  RAISE EXCEPTION 'audit_event_unordered_or_replayed' USING ERRCODE='23514';
 END IF;
 expected_hash=encode(digest(
  'M34-EVENT|'||NEW.cell_id::text||'|'||NEW.facility_id::text||'|'||
  NEW.stream_id::text||'|'||NEW.sequence::text||'|'||
  NEW.previous_sha256||'|'||p.envelope_sha256,'sha256'),'hex');
 IF NEW.event_sha256<>expected_hash THEN
  RAISE EXCEPTION 'audit_event_hash_mismatch' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $guard$;

CREATE TRIGGER m34_evidence_admission_guard
 BEFORE INSERT ON synqoria_audit.evidence_events
 FOR EACH ROW EXECUTE FUNCTION synqoria_audit.evidence_admission_guard();
REVOKE ALL ON FUNCTION synqoria_audit.evidence_admission_guard() FROM PUBLIC;
COMMIT;
