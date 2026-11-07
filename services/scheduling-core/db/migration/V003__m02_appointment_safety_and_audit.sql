-- M02 V003: prevent direct SQL forging of appointment provenance or expired patient identity.
BEGIN;
CREATE FUNCTION synqoria_schedule.appointment_intake()
RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF NEW.status<>'REQUESTED' OR NEW.version<>1 OR NEW.creator_id<>NEW.last_actor THEN
  RAISE EXCEPTION 'm02_forged_appointment_intake' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m02_appointment_initial BEFORE INSERT ON synqoria_schedule.appointments
 FOR EACH ROW EXECUTE FUNCTION synqoria_schedule.appointment_intake();

CREATE FUNCTION synqoria_schedule.current_proof()
RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='INSERT' OR (TG_OP='UPDATE' AND
  (NEW.status IN('CONFIRMED','ARRIVED','FULFILLED') OR NEW.slot_id IS DISTINCT FROM OLD.slot_id))
 THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.proof_id::text,2));
  IF NOT EXISTS(SELECT 1 FROM synqoria_schedule.patient_proofs AS proof
    WHERE proof.cell_id=NEW.cell_id AND proof.facility_id=NEW.facility_id
     AND proof.proof_id=NEW.proof_id AND proof.patient_ref=NEW.patient_ref
     AND proof.approved_by<>NEW.last_actor AND proof.revoked_at IS NULL
     AND proof.valid_from<=clock_timestamp() AND proof.valid_until>clock_timestamp())
  THEN RAISE EXCEPTION 'm02_current_patient_proof_required' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m02_appointment_live_proof
 BEFORE INSERT OR UPDATE ON synqoria_schedule.appointments
 FOR EACH ROW EXECUTE FUNCTION synqoria_schedule.current_proof();

CREATE FUNCTION synqoria_schedule.waitlist_state_guard()
RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm02_waitlist_delete_denied' USING ERRCODE='23514';END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'WAITING' OR NEW.version<>1 OR NEW.offered_slot_id IS NOT NULL
   OR NEW.appointment_id IS NOT NULL THEN
   RAISE EXCEPTION 'm02_waitlist_intake_denied' USING ERRCODE='23514';END IF;
  RETURN NEW;
 END IF;
 IF NEW.version<>OLD.version+1 OR
  ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.patient_ref,NEW.proof_id,
      NEW.resource_id,NEW.created_by,NEW.created_at)
  IS DISTINCT FROM ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.patient_ref,OLD.proof_id,
      OLD.resource_id,OLD.created_by,OLD.created_at)
  OR NOT ((OLD.status='WAITING' AND NEW.status='OFFERED') OR
          (OLD.status='OFFERED' AND NEW.status='BOOKED')) THEN
  RAISE EXCEPTION 'm02_waitlist_transition_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m02_waitlist_guard BEFORE INSERT OR UPDATE OR DELETE ON synqoria_schedule.waitlist
 FOR EACH ROW EXECUTE FUNCTION synqoria_schedule.waitlist_state_guard();

CREATE FUNCTION synqoria_schedule.appointment_audit_required()
RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM synqoria_schedule.audit_events AS ae
  JOIN synqoria_schedule.outbox AS ob ON ob.id=ae.id
  WHERE ae.cell_id=NEW.cell_id AND ae.facility_id=NEW.facility_id
   AND ae.aggregate_id=NEW.id AND ae.aggregate_type='appointment'
   AND ae.version=NEW.version AND ae.action IN (NEW.status,'REQUEST','RESCHEDULE')
   AND ob.cell_id=NEW.cell_id AND ob.facility_id=NEW.facility_id AND ob.aggregate_id=NEW.id)
 THEN RAISE EXCEPTION 'm02_unaudited_appointment' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE CONSTRAINT TRIGGER m02_appointment_audit
 AFTER INSERT OR UPDATE ON synqoria_schedule.appointments
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
 EXECUTE FUNCTION synqoria_schedule.appointment_audit_required();
CREATE TRIGGER m02_audit_no_truncate BEFORE TRUNCATE ON synqoria_schedule.audit_events
 FOR EACH STATEMENT EXECUTE FUNCTION synqoria_schedule.audit_immutable();
CREATE TRIGGER m02_outbox_no_truncate BEFORE TRUNCATE ON synqoria_schedule.outbox
 FOR EACH STATEMENT EXECUTE FUNCTION synqoria_schedule.audit_immutable();
REVOKE ALL ON FUNCTION synqoria_schedule.appointment_intake() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_schedule.current_proof() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_schedule.waitlist_state_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_schedule.appointment_audit_required() FROM PUBLIC;
COMMIT;