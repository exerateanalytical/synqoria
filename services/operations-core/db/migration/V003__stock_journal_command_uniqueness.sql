-- SYNQORIA V003 stock journal command replay uniqueness.
-- Keep the stock movement journal append-only; no runtime updates or deletes.
BEGIN;
CREATE UNIQUE INDEX stock_movement_exactly_once
ON synqoria_operations.stock_movements
 (cell_id,facility_id,actor_id,command_id);
COMMIT;
