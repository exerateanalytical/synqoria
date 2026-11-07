BEGIN;
CREATE OR REPLACE FUNCTION synqoria_schedule.appointment_transition_guard()
RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm02_appointment_delete_denied' USING ERRCODE='23514';END IF;
 IF NEW.version<>OLD.version+1 OR
 ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.patient_ref,NEW.proof_id,NEW.creator_id,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.patient_ref,OLD.proof_id,OLD.creator_id,OLD.created_at)
 OR NOT (
  (OLD.status='REQUESTED' AND NEW.status IN('CONFIRMED','CANCELLED')) OR
  (OLD.status='CONFIRMED' AND NEW.status IN('ARRIVED','CANCELLED','NO_SHOW')) OR
  (OLD.status='ARRIVED' AND NEW.status IN('FULFILLED','CANCELLED')) OR
  (OLD.status IN('REQUESTED','CONFIRMED') AND NEW.status=OLD.status AND
   NEW.slot_id<>OLD.slot_id)
 ) THEN RAISE EXCEPTION 'm02_appointment_transition_denied' USING ERRCODE='23514';END IF;
 IF OLD.status NOT IN('REQUESTED','CONFIRMED') AND NEW.slot_id<>OLD.slot_id THEN
  RAISE EXCEPTION 'm02_slot_change_denied' USING ERRCODE='23514';END IF;
 IF OLD.status<>NEW.status AND NEW.slot_id<>OLD.slot_id THEN
  RAISE EXCEPTION 'm02_transition_and_reschedule_not_atomic' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
COMMIT;