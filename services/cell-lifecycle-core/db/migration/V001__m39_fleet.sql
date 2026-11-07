-- SYNQORIA M39 Tenant and hospital-cell lifecycle (MANAGEMENT PLANE).
-- Apply with a separate DATABASE MIGRATION role, never the runtime login.
-- Provision synqoria_fleet_runtime (LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEROLE NOCREATEDB NOREPLICATION) first.
-- Boundary: this schema is the CONTROL PLANE. It records tenants, hospital cells, lifecycle, routing authority, release rollouts and migrations.
-- It holds no clinical, identity or authorization data and never reads a hospital cell's data. In this schema cell_id/facility_id identify the
-- MANAGEMENT PLANE deployment (not a hospital); hospital cells are rows in `cells`. Tenant-scoped operators see only their own tenants (forced RLS).
BEGIN;
DO $guard$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_fleet_runtime' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication AND NOT rolinherit)
 THEN RAISE EXCEPTION 'missing independent restricted synqoria_fleet_runtime'; END IF;
END $guard$;
CREATE SCHEMA IF NOT EXISTS synqoria_fleet;
REVOKE ALL ON SCHEMA synqoria_fleet FROM PUBLIC;

-- ---------------------------------------------------------------- tenants and provisioning (B01, B02)
CREATE TABLE synqoria_fleet.tenants(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 tenant_key varchar(48) NOT NULL CHECK(tenant_key ~ '^[a-z][a-z0-9-]{2,47}$'),
 name varchar(160) NOT NULL CHECK(length(btrim(name))>0), group_ref varchar(80),
 region varchar(32) NOT NULL CHECK(region ~ '^[a-z][a-z0-9-]{1,31}$'),
 residency_policy varchar(32) NOT NULL CHECK(residency_policy IN ('IN_REGION_ONLY','IN_COUNTRY_ONLY','SAME_CONTINENT')),
 status varchar(10) NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED')), version bigint NOT NULL CHECK(version>=1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,tenant_key));

CREATE TABLE synqoria_fleet.provisioning_requests(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid,
 request_key varchar(64) NOT NULL CHECK(request_key ~ '^[a-z][a-z0-9-]{2,63}$'),      -- natural idempotency key: one request per tenant key
 tenant_key varchar(48) NOT NULL CHECK(tenant_key ~ '^[a-z][a-z0-9-]{2,47}$'), tenant_name varchar(160) NOT NULL CHECK(length(btrim(tenant_name))>0),
 region varchar(32) NOT NULL, tier varchar(10) NOT NULL CHECK(tier IN ('SMALL','STANDARD','LARGE')), capacity_units int NOT NULL CHECK(capacity_units BETWEEN 1 AND 10000),
 service_profile text[] NOT NULL CHECK(cardinality(service_profile) BETWEEN 1 AND 40),
 storage_isolation varchar(20) NOT NULL CHECK(storage_isolation IN ('DEDICATED_DATABASE','DEDICATED_CLUSTER')),   -- shared schemas are not an option for a hospital cell
 residency_policy varchar(32) NOT NULL CHECK(residency_policy IN ('IN_REGION_ONLY','IN_COUNTRY_ONLY','SAME_CONTINENT')),
 rollback_metadata jsonb NOT NULL CHECK(jsonb_typeof(rollback_metadata)='object'),
 status varchar(12) NOT NULL CHECK(status IN ('REQUESTED','APPROVED','APPLIED','REJECTED','FAILED','ROLLED_BACK')),
 failure_code varchar(48), requested_by uuid NOT NULL, approved_by uuid, version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,request_key), UNIQUE(cell_id,facility_id,tenant_key),
 CONSTRAINT provisioning_checker_independent CHECK(approved_by IS NULL OR approved_by<>requested_by),
 CONSTRAINT applied_has_tenant CHECK(status NOT IN ('APPLIED') OR tenant_id IS NOT NULL));

-- ---------------------------------------------------------------- hospital cells and lifecycle (B03, B05)
CREATE TABLE synqoria_fleet.cells(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL,
 cell_key varchar(48) NOT NULL CHECK(cell_key ~ '^[a-z][a-z0-9-]{2,47}$'),
 environment varchar(8) NOT NULL CHECK(environment IN ('PROD','STAGING','DR')),
 region varchar(32) NOT NULL, tier varchar(10) NOT NULL, capacity_units int NOT NULL CHECK(capacity_units BETWEEN 1 AND 10000),
 state varchar(12) NOT NULL CHECK(state IN ('PROVISIONING','QUALIFIED','ACTIVE','DRAINING','FENCED','MIGRATING','RETIRED','FAILED')),
 placement_epoch bigint NOT NULL DEFAULT 1 CHECK(placement_epoch>=1),
 release_key varchar(40), schema_version int NOT NULL DEFAULT 1 CHECK(schema_version>=1),
 database_ref varchar(120) NOT NULL CHECK(length(btrim(database_ref))>0),                 -- opaque references; no secrets are stored here
 credential_ref varchar(120) NOT NULL CHECK(length(btrim(credential_ref))>0),
 created_by uuid NOT NULL, state_reason varchar(300), version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,cell_key),
 UNIQUE(cell_id,facility_id,database_ref), UNIQUE(cell_id,facility_id,credential_ref),     -- no database or credential is ever shared between cells
 FOREIGN KEY(cell_id,facility_id,tenant_id) REFERENCES synqoria_fleet.tenants(cell_id,facility_id,id));
CREATE INDEX cells_tenant ON synqoria_fleet.cells (cell_id,facility_id,tenant_id,state);

-- ---------------------------------------------------------------- qualification (B04)
CREATE TABLE synqoria_fleet.qualifications(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL, target_cell uuid NOT NULL,
 status varchar(8) NOT NULL CHECK(status IN ('OPEN','PASSED','FAILED')), started_by uuid NOT NULL, completed_by uuid, completed_at timestamptz,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), FOREIGN KEY(cell_id,facility_id,target_cell) REFERENCES synqoria_fleet.cells(cell_id,facility_id,id),
 CONSTRAINT qualification_completer_independent CHECK(completed_by IS NULL OR completed_by<>started_by),
 CONSTRAINT completed_is_complete CHECK((status='OPEN')=(completed_by IS NULL)));
CREATE TABLE synqoria_fleet.qualification_checks(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL, qualification_id uuid NOT NULL,
 check_key varchar(20) NOT NULL CHECK(check_key IN ('DATABASE','NETWORK','IDENTITY','STORAGE','CAPACITY','MONITORING','BACKUP','MIGRATION_COMPAT')),
 result varchar(4) NOT NULL CHECK(result IN ('PASS','FAIL')), evidence_ref varchar(160) NOT NULL CHECK(length(btrim(evidence_ref))>0), detail varchar(300),
 recorded_by uuid NOT NULL, recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,qualification_id,check_key),
 FOREIGN KEY(cell_id,facility_id,qualification_id) REFERENCES synqoria_fleet.qualifications(cell_id,facility_id,id));

-- ---------------------------------------------------------------- health, routing directory (B06)
CREATE TABLE synqoria_fleet.cell_health(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL, target_cell uuid NOT NULL,
 status varchar(9) NOT NULL CHECK(status IN ('HEALTHY','DEGRADED','UNHEALTHY')), observed_at timestamptz NOT NULL, reported_by uuid NOT NULL, release_key varchar(40),
 recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,target_cell) REFERENCES synqoria_fleet.cells(cell_id,facility_id,id));
CREATE INDEX health_latest ON synqoria_fleet.cell_health (cell_id,facility_id,target_cell,observed_at DESC);
CREATE TABLE synqoria_fleet.routes(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL, target_cell uuid NOT NULL,
 route_key varchar(120) NOT NULL CHECK(route_key ~ '^[a-z0-9][a-z0-9.-]{2,119}$'), placement_epoch bigint NOT NULL CHECK(placement_epoch>=1),
 version bigint NOT NULL CHECK(version>=1), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,route_key),
 FOREIGN KEY(cell_id,facility_id,target_cell) REFERENCES synqoria_fleet.cells(cell_id,facility_id,id));
CREATE TABLE synqoria_fleet.directory_state(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000', directory_version bigint NOT NULL CHECK(directory_version>=0),
 PRIMARY KEY(cell_id,facility_id));

-- ---------------------------------------------------------------- fleet releases (B07)
CREATE TABLE synqoria_fleet.release_manifests(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
 release_key varchar(40) NOT NULL CHECK(release_key ~ '^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$'),
 artifact_digest char(64) NOT NULL CHECK(artifact_digest ~ '^[0-9a-f]{64}$'),
 min_schema int NOT NULL CHECK(min_schema>=1), max_schema int NOT NULL CHECK(max_schema>=min_schema),
 status varchar(10) NOT NULL CHECK(status IN ('DRAFT','APPROVED','RETIRED','REJECTED')), created_by uuid NOT NULL, approved_by uuid,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,release_key),
 CONSTRAINT manifest_checker_independent CHECK(approved_by IS NULL OR approved_by<>created_by),
 CONSTRAINT approved_has_approver CHECK(status<>'APPROVED' OR approved_by IS NOT NULL));
CREATE TABLE synqoria_fleet.rollouts(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000', manifest_id uuid NOT NULL,
 status varchar(12) NOT NULL CHECK(status IN ('CANARY','PROGRESSING','COMPLETED','STOPPED')), current_stage int NOT NULL CHECK(current_stage>=0), stage_count int NOT NULL CHECK(stage_count BETWEEN 1 AND 10),
 stop_reason varchar(300), started_by uuid NOT NULL, version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), FOREIGN KEY(cell_id,facility_id,manifest_id) REFERENCES synqoria_fleet.release_manifests(cell_id,facility_id,id),
 CONSTRAINT stopped_has_reason CHECK((status='STOPPED')=(stop_reason IS NOT NULL)));
CREATE UNIQUE INDEX one_active_rollout ON synqoria_fleet.rollouts (cell_id,facility_id) WHERE status IN ('CANARY','PROGRESSING');
CREATE TABLE synqoria_fleet.rollout_cells(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL, rollout_id uuid NOT NULL, target_cell uuid NOT NULL, stage_no int NOT NULL CHECK(stage_no>=0),
 status varchar(10) NOT NULL CHECK(status IN ('PENDING','DEPLOYED','FAILED','SKIPPED')), evidence_ref varchar(160), evidence_digest char(64), deployed_at timestamptz,
 UNIQUE(cell_id,facility_id,rollout_id,target_cell),
 FOREIGN KEY(cell_id,facility_id,rollout_id) REFERENCES synqoria_fleet.rollouts(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,target_cell) REFERENCES synqoria_fleet.cells(cell_id,facility_id,id),
 CONSTRAINT deployed_has_evidence CHECK(status<>'DEPLOYED' OR (evidence_ref IS NOT NULL AND evidence_digest IS NOT NULL AND deployed_at IS NOT NULL)));

-- ---------------------------------------------------------------- cell migration (B08)
CREATE TABLE synqoria_fleet.migrations(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, tenant_id uuid NOT NULL, source_cell uuid NOT NULL, target_cell uuid NOT NULL CHECK(target_cell<>source_cell), route_id uuid NOT NULL,
 state varchar(12) NOT NULL CHECK(state IN ('PREPARED','COPYING','CAUGHT_UP','FROZEN','FENCED','VERIFIED','SWITCHED','OBSERVING','RETIRED','ROLLED_BACK','FAILED')),
 source_digest char(64), target_digest char(64), copy_evidence varchar(160), catchup_lag_ms bigint CHECK(catchup_lag_ms IS NULL OR catchup_lag_ms>=0), fence_evidence varchar(160),
 switch_epoch bigint, switch_approved_by uuid, rollback_reason varchar(300), failure_code varchar(48),
 planned_by uuid NOT NULL, version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,source_cell) REFERENCES synqoria_fleet.cells(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,target_cell) REFERENCES synqoria_fleet.cells(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,route_id) REFERENCES synqoria_fleet.routes(cell_id,facility_id,id),
 CONSTRAINT switch_independent CHECK(switch_approved_by IS NULL OR switch_approved_by<>planned_by),
 CONSTRAINT switched_has_approval CHECK(state NOT IN ('SWITCHED','OBSERVING','RETIRED') OR (switch_approved_by IS NOT NULL AND switch_epoch IS NOT NULL)),
 CONSTRAINT verified_digests_match CHECK(state NOT IN ('VERIFIED','SWITCHED','OBSERVING','RETIRED') OR (source_digest IS NOT NULL AND source_digest=target_digest)));
CREATE UNIQUE INDEX one_open_migration_per_route ON synqoria_fleet.migrations (cell_id,facility_id,route_id) WHERE state NOT IN ('RETIRED','ROLLED_BACK','FAILED');

-- ---------------------------------------------------------------- idempotency, audit, outbox
CREATE TABLE synqoria_fleet.command_idempotency(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, actor_id uuid NOT NULL, command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'), response jsonb, http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id), CHECK((response IS NULL)=(http_status IS NULL)));
CREATE TABLE synqoria_fleet.audit_events(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, aggregate_type varchar(24) NOT NULL, aggregate_id uuid NOT NULL,
 action varchar(48) NOT NULL, from_status varchar(20), to_status varchar(20) NOT NULL, aggregate_version bigint NOT NULL CHECK(aggregate_version>=1),
 actor_id uuid NOT NULL, command_id uuid NOT NULL, details jsonb NOT NULL CHECK(jsonb_typeof(details)='object'),
 digest char(64) NOT NULL CHECK(digest ~ '^[0-9a-f]{64}$'), occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id));
CREATE INDEX audit_aggregate ON synqoria_fleet.audit_events (cell_id,facility_id,aggregate_type,aggregate_id,occurred_at,event_id);
CREATE TABLE synqoria_fleet.outbox(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, event_type varchar(64) NOT NULL, aggregate_id uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_fleet.audit_events(cell_id,facility_id,event_id));
CREATE INDEX outbox_unpublished ON synqoria_fleet.outbox (created_at,event_id) WHERE published_at IS NULL;

-- ---------------------------------------------------------------- invariants
CREATE FUNCTION synqoria_fleet.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN RAISE EXCEPTION 'm39_immutable_record: % on %.% is not permitted',TG_OP,TG_TABLE_SCHEMA,TG_TABLE_NAME USING ERRCODE='integrity_constraint_violation'; END $t$;
DO $imm$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['audit_events','cell_health','qualification_checks'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON synqoria_fleet.%I FOR EACH ROW EXECUTE FUNCTION synqoria_fleet.forbid_mutation()',rel||'_immutable',rel);
  EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON synqoria_fleet.%I FOR EACH STATEMENT EXECUTE FUNCTION synqoria_fleet.forbid_mutation()',rel||'_no_truncate',rel);
 END LOOP;
END $imm$;
-- cell lifecycle: explicit permitted transitions; identity of a cell never changes; the placement epoch only rises; RETIRED is final;
-- ACTIVE requires a PASSED qualification and an approved database/credential pair
CREATE FUNCTION synqoria_fleet.protect_cell() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id OR NEW.cell_key IS DISTINCT FROM OLD.cell_key OR NEW.database_ref IS DISTINCT FROM OLD.database_ref
    OR NEW.credential_ref IS DISTINCT FROM OLD.credential_ref OR NEW.environment IS DISTINCT FROM OLD.environment OR NEW.region IS DISTINCT FROM OLD.region
    OR NEW.placement_epoch<OLD.placement_epoch OR OLD.state='RETIRED'
 THEN RAISE EXCEPTION 'm39_immutable_record: cell identity, epoch or retired state' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.state<>OLD.state AND NOT (
    (OLD.state='PROVISIONING' AND NEW.state IN ('QUALIFIED','FAILED'))
 OR (OLD.state='QUALIFIED' AND NEW.state IN ('ACTIVE','FAILED','RETIRED'))
 OR (OLD.state='ACTIVE' AND NEW.state IN ('DRAINING','FENCED'))
 OR (OLD.state='DRAINING' AND NEW.state IN ('ACTIVE','FENCED','RETIRED'))
 OR (OLD.state='FENCED' AND NEW.state IN ('ACTIVE','MIGRATING','RETIRED'))
 OR (OLD.state='MIGRATING' AND NEW.state IN ('ACTIVE','RETIRED'))
 OR (OLD.state='FAILED' AND NEW.state='RETIRED'))
 THEN RAISE EXCEPTION 'm39_invalid_transition: % -> %',OLD.state,NEW.state USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.state='ACTIVE' AND OLD.state='QUALIFIED' AND NOT EXISTS(SELECT 1 FROM synqoria_fleet.qualifications q WHERE q.target_cell=NEW.id AND q.status='PASSED')
 THEN RAISE EXCEPTION 'm39_not_qualified: activation requires a passed qualification' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER cells_protect BEFORE UPDATE OR DELETE ON synqoria_fleet.cells FOR EACH ROW EXECUTE FUNCTION synqoria_fleet.protect_cell();
-- a qualification can only PASS when all eight checks exist and passed
CREATE FUNCTION synqoria_fleet.protect_qualification() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR OLD.status<>'OPEN' OR NEW.target_cell IS DISTINCT FROM OLD.target_cell OR NEW.started_by IS DISTINCT FROM OLD.started_by
 THEN RAISE EXCEPTION 'm39_immutable_record: qualification' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.status='PASSED' AND (SELECT count(*) FROM synqoria_fleet.qualification_checks c WHERE c.qualification_id=NEW.id AND c.result='PASS')<>8
 THEN RAISE EXCEPTION 'm39_not_qualified: all eight checks must pass' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER qualifications_protect BEFORE UPDATE OR DELETE ON synqoria_fleet.qualifications FOR EACH ROW EXECUTE FUNCTION synqoria_fleet.protect_qualification();
-- routing: the epoch only rises, the route key and tenant never change
CREATE FUNCTION synqoria_fleet.protect_route() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR NEW.route_key IS DISTINCT FROM OLD.route_key OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id OR NEW.placement_epoch<OLD.placement_epoch
    OR (NEW.target_cell IS DISTINCT FROM OLD.target_cell AND NEW.placement_epoch<>OLD.placement_epoch+1)
 THEN RAISE EXCEPTION 'm39_epoch_violation: placement epochs are monotonic and tied to a cell switch' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER routes_protect BEFORE UPDATE OR DELETE ON synqoria_fleet.routes FOR EACH ROW EXECUTE FUNCTION synqoria_fleet.protect_route();
-- release manifests: content immutable once decided; provisioning/rollouts/migrations never deleted and never revive after a terminal state
CREATE FUNCTION synqoria_fleet.protect_manifest() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR (OLD.status<>'DRAFT' AND to_jsonb(NEW)-'status'-'approved_by'-'version' IS DISTINCT FROM to_jsonb(OLD)-'status'-'approved_by'-'version')
    OR (NEW.status<>OLD.status AND NOT ((OLD.status='DRAFT' AND NEW.status IN ('APPROVED','REJECTED')) OR (OLD.status='APPROVED' AND NEW.status='RETIRED')))
 THEN RAISE EXCEPTION 'm39_invalid_transition: release manifest' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER manifests_protect BEFORE UPDATE OR DELETE ON synqoria_fleet.release_manifests FOR EACH ROW EXECUTE FUNCTION synqoria_fleet.protect_manifest();
CREATE FUNCTION synqoria_fleet.protect_provisioning() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR (OLD.status IN ('REJECTED','ROLLED_BACK','APPLIED','FAILED') AND NEW.status IS DISTINCT FROM OLD.status AND NOT (OLD.status='APPLIED' AND NEW.status='ROLLED_BACK'))
    OR (OLD.status IN ('REJECTED','ROLLED_BACK','FAILED') AND NEW IS DISTINCT FROM OLD)
 THEN RAISE EXCEPTION 'm39_invalid_transition: provisioning' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE FUNCTION synqoria_fleet.protect_rollout() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR OLD.status IN ('COMPLETED','STOPPED') OR NEW.manifest_id IS DISTINCT FROM OLD.manifest_id
 THEN RAISE EXCEPTION 'm39_invalid_transition: rollout is final' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE FUNCTION synqoria_fleet.protect_migration() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR OLD.state IN ('RETIRED','ROLLED_BACK','FAILED') OR NEW.source_cell IS DISTINCT FROM OLD.source_cell OR NEW.target_cell IS DISTINCT FROM OLD.target_cell
 THEN RAISE EXCEPTION 'm39_invalid_transition: migration is final or re-targeted' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER prov_protect BEFORE UPDATE OR DELETE ON synqoria_fleet.provisioning_requests FOR EACH ROW EXECUTE FUNCTION synqoria_fleet.protect_provisioning();
CREATE TRIGGER rollouts_protect BEFORE UPDATE OR DELETE ON synqoria_fleet.rollouts FOR EACH ROW EXECUTE FUNCTION synqoria_fleet.protect_rollout();
CREATE TRIGGER migrations_protect BEFORE UPDATE OR DELETE ON synqoria_fleet.migrations FOR EACH ROW EXECUTE FUNCTION synqoria_fleet.protect_migration();

-- ---------------------------------------------------------------- grants and forced RLS (tenant scope: '*' = platform operator, else a list of tenant ids)
GRANT USAGE ON SCHEMA synqoria_fleet TO synqoria_fleet_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_fleet.tenants, synqoria_fleet.provisioning_requests, synqoria_fleet.cells, synqoria_fleet.qualifications, synqoria_fleet.routes,
 synqoria_fleet.directory_state, synqoria_fleet.release_manifests, synqoria_fleet.rollouts, synqoria_fleet.rollout_cells, synqoria_fleet.migrations, synqoria_fleet.command_idempotency TO synqoria_fleet_runtime;
GRANT SELECT,INSERT ON synqoria_fleet.qualification_checks, synqoria_fleet.cell_health, synqoria_fleet.audit_events, synqoria_fleet.outbox TO synqoria_fleet_runtime;
DO $rls$
DECLARE rel text; col text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['tenants','provisioning_requests','cells','qualifications','qualification_checks','cell_health','routes','directory_state','release_manifests','rollouts','rollout_cells','migrations','command_idempotency','audit_events','outbox'] LOOP
  col:=CASE WHEN rel='tenants' THEN 'id' WHEN rel IN ('command_idempotency','audit_events','outbox') THEN NULL ELSE 'tenant_id' END;
  EXECUTE format('ALTER TABLE synqoria_fleet.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_fleet.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('CREATE POLICY fleet_scoped_access ON synqoria_fleet.%I FOR ALL TO synqoria_fleet_runtime '||
   'USING (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid%s) '||
   'WITH CHECK (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid%s)',rel,
   CASE WHEN col IS NULL THEN '' ELSE format(' AND (current_setting(''synqoria.tenants'',true)=''*'' OR %I::text = ANY(string_to_array(current_setting(''synqoria.tenants'',true),'','')))',col) END,
   CASE WHEN col IS NULL THEN '' ELSE format(' AND (current_setting(''synqoria.tenants'',true)=''*'' OR %I::text = ANY(string_to_array(current_setting(''synqoria.tenants'',true),'','')))',col) END);
 END LOOP;
END $rls$;
COMMIT;
