-- SYNQORIA V004 — durable module-specific operational records; V001–V003 required.
-- Apply by independent migrator; runtime may mutate current operational state but cannot erase archives.
BEGIN;
CREATE TABLE synqoria_operations.procurement_orders(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,item_id uuid PRIMARY KEY,
 supplier_ref varchar(160) NOT NULL,purchase_ref varchar(160) NOT NULL,
 amount_minor bigint NOT NULL CHECK(amount_minor>=0),
 currency char(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 state varchar(24) NOT NULL DEFAULT 'ORDERED',
 receipt_ref varchar(160),invoice_ref varchar(160),
 ordered_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,item_id) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,purchase_ref),
 CHECK(state IN('ORDERED','RECEIVED','MATCHED','CLOSED'))
);
CREATE TABLE synqoria_operations.maintenance_orders(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,item_id uuid PRIMARY KEY,
 asset_ref uuid NOT NULL,
 technician_ref varchar(160),inspection_ref varchar(160),
 state varchar(32) NOT NULL DEFAULT 'OPEN',
 due_at timestamptz NOT NULL,
 verified_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,item_id) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,asset_ref) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 CHECK(state IN('OPEN','ASSIGNED','COMPLETED','VERIFIED'))
);
CREATE INDEX maintenance_asset ON synqoria_operations.maintenance_orders(cell_id,facility_id,asset_ref,due_at);
CREATE TABLE synqoria_operations.transport_assignments(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,item_id uuid PRIMARY KEY,
 vehicle_ref varchar(160) NOT NULL,driver_ref varchar(160) NOT NULL,
 origin_ref varchar(160) NOT NULL,destination_ref varchar(160) NOT NULL,
 active boolean NOT NULL DEFAULT true,
 state varchar(32) NOT NULL DEFAULT 'ASSIGNED',
 assigned_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 ended_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,item_id) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 CHECK(state IN('ASSIGNED','DISPATCHED','ARRIVED','HANDED_OVER','COMPLETED','CANCELLED','ESCALATED'))
);
CREATE UNIQUE INDEX unique_active_vehicle_dispatch
 ON synqoria_operations.transport_assignments(cell_id,facility_id,vehicle_ref) WHERE active;
CREATE INDEX dispatch_board ON synqoria_operations.transport_assignments(cell_id,facility_id,state,assigned_at);
CREATE TABLE synqoria_operations.facility_publications(
 publication_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,item_id uuid NOT NULL,
 config_key varchar(160) NOT NULL,schema_version varchar(160) NOT NULL,
 item_version bigint NOT NULL CHECK(item_version>=1),
 content_digest char(64) NOT NULL CHECK(content_digest ~ '^[0-9a-f]{64}$'),
 published_by uuid NOT NULL,
 superseded_at timestamptz,
 published_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,item_id) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,item_id,item_version)
);
CREATE UNIQUE INDEX one_current_facility_config
 ON synqoria_operations.facility_publications(cell_id,facility_id,config_key)
 WHERE superseded_at IS NULL;
CREATE TABLE synqoria_operations.document_versions(
 version_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,item_id uuid NOT NULL,
 document_version bigint NOT NULL CHECK(document_version>=1),
 disposition varchar(24) NOT NULL CHECK(disposition IN('SIGNED','AMENDMENT','RESIGNED')),
 content_digest char(64) NOT NULL CHECK(content_digest ~ '^[0-9a-f]{64}$'),
 storage_ref varchar(160) NOT NULL,
 signature_ref varchar(160),
 previous_ref varchar(160),
 actor_id uuid NOT NULL,
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,item_id) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,item_id,document_version)
);
CREATE INDEX document_lineage ON synqoria_operations.document_versions(cell_id,facility_id,item_id,document_version DESC);
CREATE TABLE synqoria_operations.notification_jobs(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,item_id uuid PRIMARY KEY,
 recipient_ref varchar(160) NOT NULL,template_ref varchar(160) NOT NULL,
 channel varchar(32) NOT NULL CHECK(channel IN('internal','email','sms','push')),
 state varchar(32) NOT NULL DEFAULT 'QUEUED',
 delivery_ref varchar(160),
 delivery_attempts integer NOT NULL DEFAULT 0 CHECK(delivery_attempts BETWEEN 0 AND 20),
 next_attempt_at timestamptz,
 acknowledged_by uuid,
 last_error_code varchar(80),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,item_id) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 CHECK(state IN('QUEUED','DELIVERED','ACKNOWLEDGED','FAILED','RETRYING','ESCALATED'))
);
CREATE INDEX notifications_pending ON synqoria_operations.notification_jobs
 (cell_id,facility_id,next_attempt_at,item_id) WHERE state IN('QUEUED','FAILED','RETRYING');
-- All tables FORCE RLS and are invisible to public / other cells/facilities.
DO $rls$
DECLARE tab text;
BEGIN
 FOREACH tab IN ARRAY ARRAY[
 'procurement_orders','maintenance_orders','transport_assignments',
 'facility_publications','document_versions','notification_jobs']
 LOOP
  EXECUTE format('ALTER TABLE synqoria_operations.%I ENABLE ROW LEVEL SECURITY',tab);
  EXECUTE format('ALTER TABLE synqoria_operations.%I FORCE ROW LEVEL SECURITY',tab);
  EXECUTE format('REVOKE ALL ON synqoria_operations.%I FROM PUBLIC',tab);
  EXECUTE format(
   'CREATE POLICY operations_scoped_domain ON synqoria_operations.%I ' ||
   'FOR ALL TO synqoria_operations_runtime ' ||
   'USING (cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) ' ||
   'WITH CHECK (cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',tab);
 END LOOP;
END $rls$;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.procurement_orders TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.maintenance_orders TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.transport_assignments TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.facility_publications TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.document_versions TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.notification_jobs TO synqoria_operations_runtime;
COMMIT;
