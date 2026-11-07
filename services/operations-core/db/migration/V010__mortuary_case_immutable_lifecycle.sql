-- M27 V010: enforce immutable decedent case identity, audited state progression
-- and the atomic custody/audit/outbox/command commit envelope at the database layer.
-- All checks apply to independent migration DBA and runtime, not just to the Java API.
BEGIN;
CREATE FUNCTION synqoria_operations.mortuary_case_transition_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $state$
DECLARE allowed boolean;
BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.state<>'RECEIVED' OR NEW.version<>1 OR
     NEW.current_storage IS NOT NULL OR NEW.released_at IS NOT NULL OR
     NEW.last_actor<>NEW.intake_actor THEN
   RAISE EXCEPTION 'mortuary_intake_state_invalid' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
 END IF;
 IF OLD.state='RELEASED' THEN
  RAISE EXCEPTION 'mortuary_released_case_immutable' USING ERRCODE='23514';
 END IF;
 IF ROW(NEW.case_id,NEW.cell_id,NEW.facility_id,NEW.decedent_ref,
  NEW.intake_ref,NEW.death_ref,NEW.originating_unit_ref,
  NEW.intake_authorization_ref,NEW.belongings_seal_ref,
  NEW.intake_receiving_staff,NEW.intake_actor,NEW.received_at)
  IS DISTINCT FROM
  ROW(OLD.case_id,OLD.cell_id,OLD.facility_id,OLD.decedent_ref,
  OLD.intake_ref,OLD.death_ref,OLD.originating_unit_ref,
  OLD.intake_authorization_ref,OLD.belongings_seal_ref,
  OLD.intake_receiving_staff,OLD.intake_actor,OLD.received_at) THEN
  RAISE EXCEPTION 'mortuary_decedent_identity_immutable' USING ERRCODE='23514';
 END IF;
 IF NEW.version<>OLD.version+1 OR NEW.updated_at<OLD.updated_at THEN
  RAISE EXCEPTION 'mortuary_case_version_invalid' USING ERRCODE='23514';
 END IF;
 allowed :=
    (OLD.state='RECEIVED' AND NEW.state='IDENTIFIED')
 OR (OLD.state='IDENTIFIED' AND NEW.state='STORED')
 OR (OLD.state='STORED' AND NEW.state='STORED'
      AND NEW.current_storage IS DISTINCT FROM OLD.current_storage)
 OR (OLD.state='STORED' AND NEW.state='RELEASE_REVIEWED')
 OR (OLD.state='RELEASE_REVIEWED' AND NEW.state='RELEASE_AUTHORIZED')
 OR (OLD.state='RELEASE_AUTHORIZED' AND NEW.state='RELEASED');
 IF NOT allowed THEN
  RAISE EXCEPTION 'mortuary_case_illegal_transition' USING ERRCODE='23514';
 END IF;
 IF OLD.state IN('STORED','RELEASE_REVIEWED') AND
    NEW.state IN('RELEASE_REVIEWED','RELEASE_AUTHORIZED') AND
    NEW.current_storage IS DISTINCT FROM OLD.current_storage THEN
  RAISE EXCEPTION 'mortuary_storage_changed_without_custody' USING ERRCODE='23514';
 END IF;
 IF NEW.state IN('RELEASE_REVIEWED','RELEASE_AUTHORIZED','RELEASED') AND
   EXISTS(SELECT 1 FROM synqoria_operations.mortuary_legal_holds
    WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id
    AND case_id=NEW.case_id AND active) THEN
  RAISE EXCEPTION 'mortuary_release_blocked_by_legal_hold' USING ERRCODE='23514';
 END IF;
 IF NEW.state='RELEASE_REVIEWED' AND
   (NEW.last_actor=NEW.intake_actor OR NOT EXISTS(
    SELECT 1 FROM synqoria_operations.mortuary_release_reviews
    WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND case_id=NEW.case_id
    AND stage='REVIEW' AND actor_id=NEW.last_actor AND case_version=NEW.version)) THEN
  RAISE EXCEPTION 'mortuary_independent_review_required' USING ERRCODE='23514';
 END IF;
 IF NEW.state='RELEASE_AUTHORIZED' AND NOT EXISTS(
    SELECT 1 FROM synqoria_operations.mortuary_release_reviews
    WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND case_id=NEW.case_id
    AND stage='AUTHORIZE' AND actor_id=NEW.last_actor AND case_version=NEW.version) THEN
  RAISE EXCEPTION 'mortuary_independent_authorization_required' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $state$;

CREATE TRIGGER mortuary_case_transition_guard
 BEFORE INSERT OR UPDATE ON synqoria_operations.mortuary_cases
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.mortuary_case_transition_guard();
REVOKE ALL ON FUNCTION synqoria_operations.mortuary_case_transition_guard() FROM PUBLIC;

-- Deferred integrity gate: all work must be present in the same transaction
-- when a case is committed. No case transition is valid with missing audit,
-- command result or outbox evidence. This also protects the initial intake.
CREATE FUNCTION synqoria_operations.mortuary_case_commit_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $commit$
BEGIN
 IF NOT EXISTS(
  SELECT 1 FROM synqoria_operations.mortuary_audit_events a
  JOIN synqoria_operations.mortuary_outbox o
   ON o.event_id=a.event_id AND o.cell_id=a.cell_id AND o.facility_id=a.facility_id
   AND o.case_id=a.case_id AND o.case_version=a.case_version
  JOIN synqoria_operations.mortuary_commands c
   ON c.cell_id=a.cell_id AND c.facility_id=a.facility_id
   AND c.actor_id=a.actor_id AND c.command_id=a.command_id
   AND c.case_id=a.case_id AND c.result_version=a.case_version
  WHERE a.cell_id=NEW.cell_id AND a.facility_id=NEW.facility_id
   AND a.case_id=NEW.case_id AND a.case_version=NEW.version
   AND a.to_state=NEW.state AND a.actor_id=NEW.last_actor
 ) THEN
  RAISE EXCEPTION 'mortuary_case_missing_transactional_evidence'
   USING ERRCODE='23514';
 END IF;
 IF NEW.state='RELEASED' AND NOT EXISTS(
  SELECT 1 FROM synqoria_operations.mortuary_custody_events e
  WHERE e.cell_id=NEW.cell_id AND e.facility_id=NEW.facility_id
   AND e.case_id=NEW.case_id AND e.case_version=NEW.version
   AND e.event_type='HANDOVER' AND e.actor_id=NEW.last_actor
   AND e.witness_staff_id IS NOT NULL AND e.belongings_ref IS NOT NULL
 ) THEN
  RAISE EXCEPTION 'mortuary_handover_custody_evidence_required'
   USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $commit$;

CREATE CONSTRAINT TRIGGER mortuary_case_commit_guard
 AFTER INSERT OR UPDATE ON synqoria_operations.mortuary_cases
 DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.mortuary_case_commit_guard();
REVOKE ALL ON FUNCTION synqoria_operations.mortuary_case_commit_guard() FROM PUBLIC;
COMMIT;
