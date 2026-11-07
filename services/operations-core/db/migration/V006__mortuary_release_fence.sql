-- SYNQORIA M27 V006 — regulatory hold/release ordering and exclusive physical bay.
-- Apply after V005 in each hospital cell by independent migration principal.
BEGIN;
CREATE UNIQUE INDEX mortuary_one_active_case_per_storage_bay
 ON synqoria_operations.mortuary_cases(cell_id,facility_id,current_storage)
 WHERE current_storage IS NOT NULL
  AND state IN ('STORED','RELEASE_REVIEWED','RELEASE_AUTHORIZED');

-- A legal hold issuer must acquire the same case row lock as a release command.
-- Without that fence, an independently entered hold can race a release commit.
CREATE FUNCTION synqoria_operations.mortuary_hold_case_fence()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER AS $hold$
DECLARE current_state varchar(32);
BEGIN
 SELECT state INTO current_state FROM synqoria_operations.mortuary_cases
 WHERE case_id=NEW.case_id AND cell_id=NEW.cell_id AND facility_id=NEW.facility_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'mortuary_case_not_found';
 END IF;
 IF NEW.active AND current_state='RELEASED' THEN
  RAISE EXCEPTION 'legal_hold_after_release_denied';
 END IF;
 RETURN NEW;
END $hold$;
CREATE TRIGGER mortuary_hold_case_lock
 BEFORE INSERT OR UPDATE OF active,case_id,cell_id,facility_id
 ON synqoria_operations.mortuary_legal_holds
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.mortuary_hold_case_fence();
REVOKE ALL ON FUNCTION synqoria_operations.mortuary_hold_case_fence() FROM PUBLIC;
COMMIT;
