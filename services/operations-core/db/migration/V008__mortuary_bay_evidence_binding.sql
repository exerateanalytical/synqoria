-- M27 V008: bind independent storage and transfer evidence to an exact physical bay.
-- Existing unbound proofs remain revocable but cannot authorize further custody changes.
BEGIN;
ALTER TABLE synqoria_operations.mortuary_authority_evidence
 ADD COLUMN storage_code varchar(160)
 CHECK(storage_code IS NULL OR storage_code ~ '^[A-Za-z0-9_.:/-]{1,160}$');

CREATE FUNCTION synqoria_operations.mortuary_bay_proof_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $guard$
BEGIN
 IF TG_OP='INSERT' THEN
  IF (NEW.for_action IN ('STORE','TRANSFER') AND NEW.storage_code IS NULL)
   OR (NEW.for_action NOT IN ('STORE','TRANSFER') AND NEW.storage_code IS NOT NULL) THEN
   RAISE EXCEPTION 'mortuary_proof_storage_scope_required' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
 END IF;
 IF NEW.storage_code IS DISTINCT FROM OLD.storage_code THEN
  RAISE EXCEPTION 'mortuary_storage_proof_rebinding_denied' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $guard$;

CREATE TRIGGER mortuary_bay_proof_guard
 BEFORE INSERT OR UPDATE ON synqoria_operations.mortuary_authority_evidence
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.mortuary_bay_proof_guard();
REVOKE ALL ON FUNCTION synqoria_operations.mortuary_bay_proof_guard() FROM PUBLIC;

CREATE INDEX mortuary_valid_evidence_storage_code
 ON synqoria_operations.mortuary_authority_evidence
 (cell_id,facility_id,case_id,for_action,expected_version,storage_code)
 WHERE revoked_at IS NULL AND for_action IN ('STORE','TRANSFER');
COMMIT;
