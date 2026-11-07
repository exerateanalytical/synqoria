-- SYNQORIA M21 M22 M23 M25 M29 M31 M37
-- Apply with separate DATABASE MIGRATION role, NEVER the runtime login.
-- Provision synqoria_operations_runtime (LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT)
-- via approved vault/DBA. Runtime identity must not own any objects.
BEGIN;
CREATE SCHEMA IF NOT EXISTS synqoria_operations;
REVOKE ALL ON SCHEMA synqoria_operations FROM PUBLIC;

CREATE TABLE synqoria_operations.items(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 module_id varchar(3) NOT NULL CHECK(module_id IN ('M21','M22','M23','M25','M29','M31','M37')),
 record_type varchar(24) NOT NULL,
 status varchar(40) NOT NULL,
 version bigint NOT NULL CHECK(version >= 1),
 created_by uuid NOT NULL,
 last_actor uuid NOT NULL,
 fields jsonb NOT NULL CHECK(jsonb_typeof(fields)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 CHECK(octet_length(fields::text) <= 16384)
);
CREATE INDEX items_cell_facility_module ON synqoria_operations.items
 (cell_id,facility_id,module_id,updated_at DESC,id);

CREATE TABLE synqoria_operations.command_idempotency(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 item_id uuid,
 result_version bigint CHECK(result_version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id),
 CONSTRAINT command_done_atomic CHECK((item_id IS NULL)=(result_version IS NULL)),
 FOREIGN KEY(cell_id,facility_id,item_id)
   REFERENCES synqoria_operations.items(cell_id,facility_id,id)
);
CREATE INDEX command_scope_item ON synqoria_operations.command_idempotency (cell_id,facility_id,item_id);

CREATE TABLE synqoria_operations.audit_events(
 event_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 item_id uuid NOT NULL,
 module_id varchar(3) NOT NULL,
 action varchar(64) NOT NULL,
 from_status varchar(40),
 to_status varchar(40) NOT NULL,
 item_version bigint NOT NULL CHECK(item_version >= 1),
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 field_digest char(64) NOT NULL CHECK(field_digest ~ '^[0-9a-f]{64}$'),
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id),
 FOREIGN KEY(cell_id,facility_id,item_id)
  REFERENCES synqoria_operations.items(cell_id,facility_id,id)
);
CREATE INDEX audit_events_item_time ON synqoria_operations.audit_events
 (cell_id,facility_id,item_id,occurred_at DESC);

CREATE TABLE synqoria_operations.outbox(
 event_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 item_id uuid NOT NULL,
 module_id varchar(3) NOT NULL,
 event_type varchar(64) NOT NULL,
 event_version bigint NOT NULL CHECK(event_version >= 1),
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id)
  REFERENCES synqoria_operations.audit_events(cell_id,facility_id,event_id)
);
CREATE INDEX outbox_unpublished ON synqoria_operations.outbox (created_at,event_id)
 WHERE published_at IS NULL;

-- M22 only: all stock balance changes require atomic row locks, a
-- signed movement, and zero-negative stock invariants.
CREATE TABLE synqoria_operations.stock_balances(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 item_id uuid NOT NULL,
 lot_code varchar(160) NOT NULL,
 bin_code varchar(160) NOT NULL,
 on_hand bigint NOT NULL DEFAULT 0 CHECK(on_hand >= 0),
 reserved bigint NOT NULL DEFAULT 0 CHECK(reserved >= 0 AND reserved <= on_hand),
 version bigint NOT NULL DEFAULT 1 CHECK(version >= 1),
 PRIMARY KEY(cell_id,facility_id,item_id,lot_code,bin_code),
 FOREIGN KEY(cell_id,facility_id,item_id)
  REFERENCES synqoria_operations.items(cell_id,facility_id,id)
);
CREATE TABLE synqoria_operations.stock_movements(
 movement_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 item_id uuid NOT NULL,
 lot_code varchar(160) NOT NULL,
 bin_code varchar(160) NOT NULL,
 movement varchar(20) NOT NULL CHECK(movement IN ('RECEIVE','RESERVE','ISSUE','RELEASE','RETURN','ADJUST')),
 quantity bigint NOT NULL CHECK(quantity > 0),
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 reference_code varchar(160) NOT NULL,
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,item_id,lot_code,bin_code)
  REFERENCES synqoria_operations.stock_balances(cell_id,facility_id,item_id,lot_code,bin_code)
);
CREATE INDEX stock_movements_item ON synqoria_operations.stock_movements
 (cell_id,facility_id,item_id,occurred_at DESC);

-- No open PUBLIC grants; scoped runtime auth is verified separately.
DO $sql$
DECLARE name text;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_operations_runtime'
  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole
  AND NOT rolcreatedb AND NOT rolreplication AND NOT rolinherit)
 THEN RAISE EXCEPTION 'missing independent restricted synqoria_operations_runtime';
 END IF;
END $sql$;
GRANT USAGE ON SCHEMA synqoria_operations TO synqoria_operations_runtime;

DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['items','command_idempotency','audit_events','outbox',
                             'stock_balances','stock_movements']
 LOOP
  EXECUTE format('ALTER TABLE synqoria_operations.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_operations.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format(
   'CREATE POLICY operations_scoped_access ON synqoria_operations.%I ' ||
   'FOR ALL TO synqoria_operations_runtime ' ||
   'USING (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) ' ||
   'WITH CHECK (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;

GRANT SELECT,INSERT,UPDATE ON synqoria_operations.items TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.command_idempotency TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.audit_events TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.outbox TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.stock_balances TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.stock_movements TO synqoria_operations_runtime;
COMMIT;
