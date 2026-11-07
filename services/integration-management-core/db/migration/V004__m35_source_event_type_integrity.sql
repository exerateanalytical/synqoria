-- M35 V004: issuer attestation includes event type; PostgreSQL admission independently
-- checks event+digest+partner identity even when Java validation is bypassed.
BEGIN;
ALTER TABLE synqoria_integration.partner_proofs
 ADD COLUMN source_event_type varchar(64)
 CHECK(source_event_type IS NULL OR source_event_type ~ '^[A-Z][A-Z0-9_]{1,63}$');
ALTER TABLE synqoria_integration.partner_proofs
 ADD CONSTRAINT m35_event_type_exact
 CHECK((proof_type='SOURCE_MESSAGE')=(source_event_type IS NOT NULL));
CREATE OR REPLACE FUNCTION synqoria_integration.proof_guard()
RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm35_proof_delete_denied' USING ERRCODE='23514';END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(OLD.proof_id::text,35));
 IF ROW(NEW.proof_id,NEW.cell_id,NEW.facility_id,NEW.partner_code,NEW.proof_type,
  NEW.source_event_id,NEW.source_payload_sha256,NEW.source_event_type,NEW.external_actor,NEW.issuer,
  NEW.valid_from,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.proof_id,OLD.cell_id,OLD.facility_id,OLD.partner_code,OLD.proof_type,
  OLD.source_event_id,OLD.source_payload_sha256,OLD.source_event_type,OLD.external_actor,OLD.issuer,
  OLD.valid_from,OLD.created_at)
 OR NEW.valid_until>OLD.valid_until OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at)
 THEN RAISE EXCEPTION 'm35_proof_rebinding_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE FUNCTION synqoria_integration.message_admission() RETURNS trigger LANGUAGE plpgsql AS $f$
DECLARE p record;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(NEW.source_proof_id::text,35));
 SELECT proof.proof_id,proof.external_actor INTO p FROM synqoria_integration.partner_proofs proof
 JOIN synqoria_integration.partners partner
  ON partner.cell_id=proof.cell_id AND partner.facility_id=proof.facility_id
   AND partner.partner_code=proof.partner_code
 WHERE proof.cell_id=NEW.cell_id AND proof.facility_id=NEW.facility_id
  AND proof.proof_id=NEW.source_proof_id AND proof.proof_type='SOURCE_MESSAGE'
  AND proof.source_event_id=NEW.external_event_id
  AND proof.source_payload_sha256=NEW.payload_sha256
  AND proof.source_event_type=NEW.event_type
  AND proof.external_actor<>NEW.received_by
  AND proof.revoked_at IS NULL AND proof.valid_from<=clock_timestamp()
  AND proof.valid_until>clock_timestamp()
  AND partner.id=NEW.partner_id AND partner.status='ENABLED';
 IF NOT FOUND THEN
  RAISE EXCEPTION 'm35_message_issuer_proof_missing' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m35_message_admission_guard BEFORE INSERT ON synqoria_integration.messages
 FOR EACH ROW EXECUTE FUNCTION synqoria_integration.message_admission();
REVOKE ALL ON FUNCTION synqoria_integration.message_admission() FROM PUBLIC;
COMMIT;