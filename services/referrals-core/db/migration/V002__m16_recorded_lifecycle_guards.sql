BEGIN;
CREATE FUNCTION synqoria_referral.intake_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF NEW.status<>'DRAFT' OR NEW.version<>1 OR NEW.authorized_by IS NOT NULL
 OR NEW.sent_by IS NOT NULL OR NEW.acceptance_actor IS NOT NULL
 OR NEW.receiving_proof_id IS NOT NULL OR NEW.handover_proof_id IS NOT NULL
 OR NEW.followup_proof_id IS NOT NULL OR NEW.decision IS NOT NULL
 OR NEW.closed_at IS NOT NULL OR NEW.attended_at IS NOT NULL
 THEN RAISE EXCEPTION 'm16_forged_intake_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m16_initial_state BEFORE INSERT ON synqoria_referral.referrals
 FOR EACH ROW EXECUTE FUNCTION synqoria_referral.intake_guard();

CREATE FUNCTION synqoria_referral.recorded_history_required() RETURNS trigger LANGUAGE plpgsql AS $f$
DECLARE from_status varchar(16);
BEGIN
 SELECT from_status INTO from_status FROM synqoria_referral.transition_events
  WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id
   AND referral_id=NEW.id AND version=NEW.version AND to_status=NEW.status;
 IF NOT FOUND OR
 NOT EXISTS(SELECT 1 FROM synqoria_referral.audit_events
  WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id
   AND aggregate_id=NEW.id AND action=NEW.status AND version=NEW.version)
 OR NOT EXISTS(SELECT 1 FROM synqoria_referral.outbox
  WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND aggregate_id=NEW.id
   AND event_type='m16.referral.'||NEW.status)
 THEN RAISE EXCEPTION 'm16_unaudited_transition_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE CONSTRAINT TRIGGER m16_audit_required AFTER INSERT OR UPDATE ON synqoria_referral.referrals
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
 EXECUTE FUNCTION synqoria_referral.recorded_history_required();
REVOKE ALL ON FUNCTION synqoria_referral.intake_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_referral.recorded_history_required() FROM PUBLIC;
COMMIT;