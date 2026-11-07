-- V002: M32 keeps external authority evidence runtime SELECT-only.
-- PostgreSQL SELECT FOR SHARE would require UPDATE permission; instead both the
-- reader and the independent issuer's revocation trigger serialize by proof UUID.
-- The advisory lock is transaction-scoped and held until command/audit commit.
BEGIN;
CREATE OR REPLACE FUNCTION synqoria_privacy.proof_immutable()
RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(OLD.proof_id::text,32));
 IF TG_OP='DELETE' THEN
  RAISE EXCEPTION 'privacy_evidence_not_deletable' USING ERRCODE='23514';
 END IF;
 IF ROW(NEW.proof_id,NEW.cell_id,NEW.facility_id,NEW.subject_ref,NEW.purpose,NEW.recipient,
  NEW.operation,NEW.source_ref,NEW.issuer,NEW.approved_by,NEW.valid_from,NEW.digest_sha256,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.proof_id,OLD.cell_id,OLD.facility_id,OLD.subject_ref,OLD.purpose,OLD.recipient,
  OLD.operation,OLD.source_ref,OLD.issuer,OLD.approved_by,OLD.valid_from,OLD.digest_sha256,OLD.created_at)
 OR NEW.valid_until>OLD.valid_until
 OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN
  RAISE EXCEPTION 'privacy_evidence_rebinding_denied' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;
REVOKE ALL ON FUNCTION synqoria_privacy.proof_immutable() FROM PUBLIC;
COMMIT;
