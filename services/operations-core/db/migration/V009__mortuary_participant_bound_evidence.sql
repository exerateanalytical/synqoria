-- M27 V009: bind independent evidence to actual custody receiver, witness and recipient.
-- Historical unbound proofs remain revocable but fail closed at the new runtime gate.
BEGIN;
ALTER TABLE synqoria_operations.mortuary_authority_evidence
 ADD COLUMN receiving_staff_id uuid,
 ADD COLUMN witness_staff_id uuid,
 ADD COLUMN recipient_ref varchar(160)
 CHECK(recipient_ref IS NULL OR recipient_ref ~ '^[A-Za-z0-9_.:/-]{1,160}$');

CREATE FUNCTION synqoria_operations.mortuary_participant_proof_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $scope$
DECLARE needs_receiver boolean;
DECLARE needs_witness boolean;
DECLARE needs_recipient boolean;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.receiving_staff_id,NEW.witness_staff_id,NEW.recipient_ref)
    IS DISTINCT FROM ROW(OLD.receiving_staff_id,OLD.witness_staff_id,OLD.recipient_ref) THEN
   RAISE EXCEPTION 'mortuary_proof_participant_rebinding_denied' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
 END IF;
 needs_receiver := NEW.proof_kind IN ('STORAGE_SAFE','CUSTODY_ACCEPTED');
 needs_witness := NEW.proof_kind IN ('HANDOVER_RECEIPT','BELONGINGS_RELEASED');
 needs_recipient := NEW.proof_kind IN ('LEGAL_RELEASE','RECIPIENT_VERIFIED',
  'RELEASE_AUTHORIZATION','HANDOVER_RECEIPT','BELONGINGS_RELEASED');
 IF (NEW.receiving_staff_id IS NOT NULL) <> needs_receiver
   OR (NEW.witness_staff_id IS NOT NULL) <> needs_witness
   OR (NEW.recipient_ref IS NOT NULL) <> needs_recipient THEN
  RAISE EXCEPTION 'mortuary_proof_participant_scope_required' USING ERRCODE='23514';
 END IF;
 IF (NEW.receiving_staff_id IS NOT NULL AND NEW.receiving_staff_id=NEW.approved_by)
   OR (NEW.witness_staff_id IS NOT NULL AND NEW.witness_staff_id=NEW.approved_by) THEN
  RAISE EXCEPTION 'mortuary_authority_must_be_independent' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $scope$;

CREATE TRIGGER mortuary_participant_proof_guard
 BEFORE INSERT OR UPDATE ON synqoria_operations.mortuary_authority_evidence
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.mortuary_participant_proof_guard();
REVOKE ALL ON FUNCTION synqoria_operations.mortuary_participant_proof_guard() FROM PUBLIC;

CREATE INDEX mortuary_participant_proof_lookup
 ON synqoria_operations.mortuary_authority_evidence
 (cell_id,facility_id,case_id,for_action,proof_kind,receiving_staff_id,witness_staff_id,recipient_ref)
 WHERE revoked_at IS NULL;
COMMIT;
