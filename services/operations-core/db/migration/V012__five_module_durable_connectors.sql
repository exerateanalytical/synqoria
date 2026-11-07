-- SYNQORIA V012: bounded durable adapters for M21/M25/M37; no external credentials or fake deliveries.
-- Apply with independent migration role after V001-V011.
BEGIN;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE synqoria_operations.procurement_export_queue(
  export_id uuid PRIMARY KEY,
  cell_id uuid NOT NULL, facility_id uuid NOT NULL, item_id uuid NOT NULL,
  item_version bigint NOT NULL CHECK(item_version>=1),
  purchase_ref varchar(160) NOT NULL,
  amount_minor bigint NOT NULL CHECK(amount_minor>=0),
  currency char(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
  receipt_ref varchar(160) NOT NULL,
  invoice_ref varchar(160) NOT NULL,
  export_digest char(64) NOT NULL CHECK(export_digest ~ '^[0-9a-f]{64}$'),
  state varchar(12) NOT NULL DEFAULT 'PENDING' CHECK(state IN('PENDING','ACKED')),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE(cell_id,facility_id,item_id,item_version),
  FOREIGN KEY(cell_id,facility_id,item_id) REFERENCES synqoria_operations.items(cell_id,facility_id,id)
);
CREATE INDEX procurement_exports_pending ON synqoria_operations.procurement_export_queue(cell_id,facility_id,created_at,export_id) WHERE state='PENDING';
CREATE TABLE synqoria_operations.vehicle_reservations(
  reservation_id uuid PRIMARY KEY,
  cell_id uuid NOT NULL, facility_id uuid NOT NULL, vehicle_id uuid NOT NULL,
  requested_by uuid NOT NULL,
  start_at timestamptz NOT NULL, end_at timestamptz NOT NULL,
  reservation_ref varchar(160) NOT NULL,
  status varchar(12) NOT NULL DEFAULT 'RESERVED' CHECK(status IN('RESERVED','CANCELLED')),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  cancelled_by uuid,
  CHECK(end_at>start_at),
  CHECK((status='RESERVED' AND cancelled_by IS NULL) OR (status='CANCELLED' AND cancelled_by IS NOT NULL)),
  UNIQUE(cell_id,facility_id,reservation_ref),
  FOREIGN KEY(cell_id,facility_id,vehicle_id) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
  EXCLUDE USING gist (cell_id WITH =,facility_id WITH =,vehicle_id WITH =,
    tstzrange(start_at,end_at,'[)') WITH &&) WHERE (status='RESERVED')
);
CREATE INDEX reservations_current ON synqoria_operations.vehicle_reservations(cell_id,facility_id,vehicle_id,start_at,end_at) WHERE status='RESERVED';
ALTER TABLE synqoria_operations.notification_jobs
 ADD COLUMN lease_id uuid,
 ADD COLUMN lease_until timestamptz,
 ADD CONSTRAINT notification_lease_pair CHECK ((lease_id IS NULL)=(lease_until IS NULL));
CREATE INDEX notification_lease_available ON synqoria_operations.notification_jobs
 (cell_id,facility_id,next_attempt_at,lease_until) WHERE state IN('QUEUED','FAILED','RETRYING');
DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['procurement_export_queue','vehicle_reservations']
 LOOP
  EXECUTE format('ALTER TABLE synqoria_operations.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_operations.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('REVOKE ALL ON synqoria_operations.%I FROM PUBLIC',rel);
  EXECUTE format(
    'CREATE POLICY operations_scoped_durable ON synqoria_operations.%I FOR ALL TO synqoria_operations_runtime ' ||
    'USING (cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND ' ||
    'facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) ' ||
    'WITH CHECK (cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND ' ||
    'facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;
GRANT SELECT,INSERT ON synqoria_operations.procurement_export_queue TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.vehicle_reservations TO synqoria_operations_runtime;
COMMIT;
