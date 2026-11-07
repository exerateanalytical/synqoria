-- SYNQORIA V011: M22 independently reviewed stock counts; no automatic stock balance adjustment.
-- Migration role, never application runtime, applies this file after V001-V010.
BEGIN;
CREATE TABLE synqoria_operations.stock_counts(
 count_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 item_id uuid NOT NULL,
 lot_code varchar(160) NOT NULL,
 bin_code varchar(160) NOT NULL,
 expected_on_hand bigint NOT NULL CHECK(expected_on_hand>=0),
 expected_reserved bigint NOT NULL CHECK(expected_reserved>=0 AND expected_reserved<=expected_on_hand),
 counted_on_hand bigint NOT NULL CHECK(counted_on_hand>=0),
 stock_version bigint NOT NULL CHECK(stock_version>=1),
 state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN('PENDING','REVIEWED','REJECTED')),
 requested_by uuid NOT NULL,
 reviewed_by uuid,
 command_id uuid NOT NULL,
 requested_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 reviewed_at timestamptz,
 CHECK ((reviewed_by IS NULL AND reviewed_at IS NULL AND state='PENDING') OR
        (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND state<>'PENDING' AND reviewed_by<>requested_by)),
 UNIQUE(cell_id,facility_id,requested_by,command_id),
 FOREIGN KEY(cell_id,facility_id,item_id,lot_code,bin_code)
 REFERENCES synqoria_operations.stock_balances(cell_id,facility_id,item_id,lot_code,bin_code)
);
CREATE INDEX stock_counts_pending ON synqoria_operations.stock_counts
 (cell_id,facility_id,requested_at,count_id) WHERE state='PENDING';
ALTER TABLE synqoria_operations.stock_counts ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_operations.stock_counts FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_operations.stock_counts FROM PUBLIC;
CREATE POLICY operations_scoped_counts ON synqoria_operations.stock_counts
 FOR ALL TO synqoria_operations_runtime
 USING(cell_id=NULLIF(current_setting('synqoria.cell',true),'')::uuid AND
       facility_id=NULLIF(current_setting('synqoria.facility',true),'')::uuid)
 WITH CHECK(cell_id=NULLIF(current_setting('synqoria.cell',true),'')::uuid AND
       facility_id=NULLIF(current_setting('synqoria.facility',true),'')::uuid);
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.stock_counts TO synqoria_operations_runtime;
-- Count identity, observed quantities and maker cannot be altered after capture.
CREATE FUNCTION synqoria_operations.guard_stock_count() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
 IF (OLD.count_id,OLD.cell_id,OLD.facility_id,OLD.item_id,OLD.lot_code,OLD.bin_code,
     OLD.expected_on_hand,OLD.expected_reserved,OLD.counted_on_hand,OLD.stock_version,
     OLD.requested_by,OLD.command_id,OLD.requested_at)
 IS DISTINCT FROM
    (NEW.count_id,NEW.cell_id,NEW.facility_id,NEW.item_id,NEW.lot_code,NEW.bin_code,
     NEW.expected_on_hand,NEW.expected_reserved,NEW.counted_on_hand,NEW.stock_version,
     NEW.requested_by,NEW.command_id,NEW.requested_at)
 THEN RAISE EXCEPTION 'immutable_count_fields'; END IF;
 IF OLD.state<>'PENDING' OR NEW.state='PENDING' OR NEW.reviewed_by=OLD.requested_by
 THEN RAISE EXCEPTION 'invalid_count_review'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER stock_count_review_guard BEFORE UPDATE ON synqoria_operations.stock_counts
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.guard_stock_count();
COMMIT;
