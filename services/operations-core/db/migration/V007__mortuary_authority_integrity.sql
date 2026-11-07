-- M27 V007: independent authority records are append-or-revoke, never rewrite/delete.
-- Locks for allowed revocations/rescissions remain enforced by V006's case/advisory fences.
-- Issuer roles may create a new proof/hold; they may not repurpose existing evidence.
BEGIN;

CREATE FUNCTION synqoria_operations.mortuary_evidence_mutation_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $guard$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'mortuary_evidence_delete_denied' USING ERRCODE='23514';
 END IF;
 IF ROW(NEW.proof_id, NEW.cell_id, NEW.facility_id, NEW.decedent_ref,
  NEW.case_id, NEW.for_action, NEW.expected_version, NEW.proof_kind,
  NEW.external_ref, NEW.authority_issuer, NEW.approved_by, NEW.valid_from,
  NEW.digest_sha256, NEW.issued_at)
  IS DISTINCT FROM
  ROW(OLD.proof_id, OLD.cell_id, OLD.facility_id, OLD.decedent_ref,
  OLD.case_id, OLD.for_action, OLD.expected_version, OLD.proof_kind,
  OLD.external_ref, OLD.authority_issuer, OLD.approved_by, OLD.valid_from,
  OLD.digest_sha256, OLD.issued_at) THEN
  RAISE EXCEPTION 'mortuary_evidence_identity_immutable' USING ERRCODE='23514';
 END IF;
 IF NEW.valid_until > OLD.valid_until THEN
  RAISE EXCEPTION 'mortuary_evidence_expiry_extension_denied' USING ERRCODE='23514';
 END IF;
 IF OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at THEN
  RAISE EXCEPTION 'mortuary_evidence_revocation_irreversible' USING ERRCODE='23514';
 END IF;
 IF NEW.revoked_at IS NOT NULL AND NEW.revoked_at < OLD.issued_at THEN
  RAISE EXCEPTION 'mortuary_evidence_invalid_revocation_time' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $guard$;

CREATE TRIGGER mortuary_evidence_mutation_guard
 BEFORE UPDATE OR DELETE ON synqoria_operations.mortuary_authority_evidence
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.mortuary_evidence_mutation_guard();
REVOKE ALL ON FUNCTION synqoria_operations.mortuary_evidence_mutation_guard() FROM PUBLIC;

CREATE FUNCTION synqoria_operations.mortuary_hold_mutation_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $guard$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'mortuary_hold_delete_denied' USING ERRCODE='23514';
 END IF;
 IF ROW(NEW.hold_id, NEW.cell_id, NEW.facility_id, NEW.case_id,
  NEW.authority_ref, NEW.reason_code, NEW.ordered_by, NEW.ordered_at)
  IS DISTINCT FROM
  ROW(OLD.hold_id, OLD.cell_id, OLD.facility_id, OLD.case_id,
  OLD.authority_ref, OLD.reason_code, OLD.ordered_by, OLD.ordered_at) THEN
  RAISE EXCEPTION 'mortuary_hold_basis_immutable' USING ERRCODE='23514';
 END IF;
 IF NOT OLD.active AND NEW.active THEN
  RAISE EXCEPTION 'mortuary_hold_reactivation_denied' USING ERRCODE='23514';
 END IF;
 IF OLD.rescinded_at IS NOT NULL AND NEW.rescinded_at IS DISTINCT FROM OLD.rescinded_at THEN
  RAISE EXCEPTION 'mortuary_hold_rescission_immutable' USING ERRCODE='23514';
 END IF;
 IF OLD.active AND NOT NEW.active AND
  (NEW.rescinded_at IS NULL OR NEW.rescinded_at < OLD.ordered_at) THEN
  RAISE EXCEPTION 'mortuary_hold_invalid_rescission' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $guard$;

CREATE TRIGGER mortuary_hold_mutation_guard
 BEFORE UPDATE OR DELETE ON synqoria_operations.mortuary_legal_holds
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.mortuary_hold_mutation_guard();
REVOKE ALL ON FUNCTION synqoria_operations.mortuary_hold_mutation_guard() FROM PUBLIC;
COMMIT;
