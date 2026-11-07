-- SYNQORIA M28 Hospital operations and business intelligence (read/analytics side).
-- Apply with a separate DATABASE MIGRATION role, never the runtime login.
-- Provision synqoria_analytics_runtime (LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEROLE NOCREATEDB NOREPLICATION) first.
-- Boundaries: this schema holds PROJECTIONS of events from verified upstream sources with provenance. It never writes to,
-- and is never a substitute for, clinical, scheduling or financial authorities. A hospital cell can never read another
-- cell's rows (forced RLS on cell_id). Published numbers are aggregates with small-cell suppression; unknown is never zero.
BEGIN;
DO $guard$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_analytics_runtime' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication AND NOT rolinherit)
 THEN RAISE EXCEPTION 'missing independent restricted synqoria_analytics_runtime'; END IF;
END $guard$;
CREATE SCHEMA IF NOT EXISTS synqoria_analytics;
REVOKE ALL ON SCHEMA synqoria_analytics FROM PUBLIC;

-- ------------------------------------------------------------------ sources and ingestion (B01, B02)
CREATE TABLE synqoria_analytics.sources(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 source_key varchar(48) NOT NULL CHECK(source_key ~ '^[a-z][a-z0-9_.-]{1,47}$'),
 name varchar(160) NOT NULL CHECK(length(btrim(name))>0),
 domain varchar(10) NOT NULL CHECK(domain IN ('OPERATIONS','CLINICAL','FINANCIAL')),
 owner_ref varchar(120) NOT NULL CHECK(length(btrim(owner_ref))>0),             -- the upstream authority that owns the data
 contract_version int NOT NULL CHECK(contract_version>=1),
 required_fields text[] NOT NULL CHECK(cardinality(required_fields)>=1 AND cardinality(required_fields)<=40),
 max_lateness_minutes int NOT NULL CHECK(max_lateness_minutes BETWEEN 1 AND 43200),
 max_staleness_minutes int NOT NULL CHECK(max_staleness_minutes BETWEEN 1 AND 43200),
 status varchar(10) NOT NULL CHECK(status IN ('PENDING','VERIFIED','SUSPENDED')),
 registered_by uuid NOT NULL, verified_by uuid, verified_at timestamptz,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,source_key),
 CONSTRAINT source_verifier_independent CHECK(verified_by IS NULL OR verified_by<>registered_by),
 CONSTRAINT verified_has_evidence CHECK(status<>'VERIFIED' OR (verified_by IS NOT NULL AND verified_at IS NOT NULL)));

CREATE TABLE synqoria_analytics.ingest_events(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, source_id uuid NOT NULL,
 source_event_id varchar(120) NOT NULL CHECK(length(source_event_id) BETWEEN 1 AND 120),
 entity_key varchar(120) NOT NULL CHECK(length(entity_key) BETWEEN 1 AND 120),
 source_version bigint NOT NULL CHECK(source_version>=1),
 occurred_at timestamptz NOT NULL, received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object' AND octet_length(payload::text)<=32768),
 payload_hash char(64) NOT NULL CHECK(payload_hash ~ '^[0-9a-f]{64}$'),
 status varchar(11) NOT NULL CHECK(status IN ('APPLIED','SUPERSEDED','QUARANTINED','DISCARDED')),
 late boolean NOT NULL DEFAULT false,
 problems text[] NOT NULL DEFAULT '{}', retry_count int NOT NULL DEFAULT 0 CHECK(retry_count BETWEEN 0 AND 20),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,source_id,source_event_id),
 FOREIGN KEY(cell_id,facility_id,source_id) REFERENCES synqoria_analytics.sources(cell_id,facility_id,id),
 CONSTRAINT quarantine_has_reasons CHECK((status='QUARANTINED')=(cardinality(problems)>0) OR status='DISCARDED'));
CREATE INDEX ingest_entity ON synqoria_analytics.ingest_events (cell_id,facility_id,source_id,entity_key,source_version DESC);
CREATE INDEX ingest_quarantine ON synqoria_analytics.ingest_events (cell_id,facility_id,received_at) WHERE status='QUARANTINED';

-- current state per source entity: a rebuildable projection of ingest_events
CREATE TABLE synqoria_analytics.facts(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, source_id uuid NOT NULL, entity_key varchar(120) NOT NULL,
 source_version bigint NOT NULL CHECK(source_version>=1), occurred_at timestamptz NOT NULL,
 data jsonb NOT NULL CHECK(jsonb_typeof(data)='object'), event_id uuid NOT NULL,
 PRIMARY KEY(cell_id,facility_id,source_id,entity_key),
 FOREIGN KEY(cell_id,facility_id,source_id) REFERENCES synqoria_analytics.sources(cell_id,facility_id,id));
CREATE INDEX facts_time ON synqoria_analytics.facts (cell_id,facility_id,source_id,occurred_at);

-- ------------------------------------------------------------------ metric registry and values (B03-B06)
CREATE TABLE synqoria_analytics.metric_definitions(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 metric_key varchar(64) NOT NULL CHECK(metric_key ~ '^[a-z][a-z0-9_.]{1,63}$'), version_no int NOT NULL CHECK(version_no>=1),
 name_en varchar(160) NOT NULL CHECK(length(btrim(name_en))>0), name_fr varchar(160) NOT NULL CHECK(length(btrim(name_fr))>0),
 domain varchar(10) NOT NULL CHECK(domain IN ('OPERATIONS','CLINICAL','FINANCIAL')),
 unit varchar(16) NOT NULL CHECK(unit IN ('COUNT','RATIO','PERCENT','AMOUNT','MINUTES')),
 source_key varchar(48) NOT NULL, definition jsonb NOT NULL CHECK(jsonb_typeof(definition)='object'),
 definition_hash char(64) NOT NULL CHECK(definition_hash ~ '^[0-9a-f]{64}$'),
 min_cell int NOT NULL CHECK(min_cell BETWEEN 1 AND 1000),
 effective_from date NOT NULL, status varchar(10) NOT NULL CHECK(status IN ('DRAFT','APPROVED','RETIRED','REJECTED')),
 created_by uuid NOT NULL, approved_by uuid, approved_at timestamptz,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,metric_key,version_no),
 CONSTRAINT metric_approver_independent CHECK(approved_by IS NULL OR approved_by<>created_by),
 CONSTRAINT metric_approved CHECK(status NOT IN ('APPROVED','RETIRED') OR approved_by IS NOT NULL),
 -- statistical disclosure control: patient-level domains cannot publish small groups
 CONSTRAINT clinical_min_cell CHECK(domain='FINANCIAL' OR min_cell>=5));
CREATE UNIQUE INDEX one_approved_metric ON synqoria_analytics.metric_definitions (cell_id,facility_id,metric_key) WHERE status='APPROVED';

CREATE TABLE synqoria_analytics.metric_values(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, metric_id uuid NOT NULL,
 metric_key varchar(64) NOT NULL, domain varchar(10) NOT NULL, unit varchar(16) NOT NULL, metric_version int NOT NULL,
 period_start date NOT NULL, period_end date NOT NULL, dimension_key varchar(120) NOT NULL, currency char(3),
 state varchar(10) NOT NULL CHECK(state IN ('OK','SUPPRESSED','NO_DATA','INCOMPLETE')),
 numerator numeric(24,4), denominator numeric(24,4), value numeric(24,6),
 cohort_size int NOT NULL CHECK(cohort_size>=0), unknown_count int NOT NULL CHECK(unknown_count>=0),
 source_watermark timestamptz, stale boolean NOT NULL DEFAULT false, computed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,metric_id) REFERENCES synqoria_analytics.metric_definitions(cell_id,facility_id,id),
 CHECK(period_end>=period_start),
 -- unknown is never zero: no value unless the state is OK/INCOMPLETE with a real cohort; suppressed and empty rows carry no numbers
 CONSTRAINT no_fabricated_numbers CHECK((state IN ('SUPPRESSED','NO_DATA') AND numerator IS NULL AND denominator IS NULL AND value IS NULL)
   OR (state IN ('OK','INCOMPLETE') AND numerator IS NOT NULL AND cohort_size>0)));
CREATE INDEX values_lookup ON synqoria_analytics.metric_values (cell_id,facility_id,metric_id,period_start DESC);
-- one value per metric, period, dimension AND currency: amounts in different currencies are never merged
CREATE UNIQUE INDEX values_unique ON synqoria_analytics.metric_values (cell_id,facility_id,metric_id,period_start,period_end,dimension_key,coalesce(currency,'   '));

-- ------------------------------------------------------------------ reports, runs, schedules, exports (B07, B08)
CREATE TABLE synqoria_analytics.report_definitions(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 report_key varchar(64) NOT NULL CHECK(report_key ~ '^[a-z][a-z0-9_.]{1,63}$'), version_no int NOT NULL CHECK(version_no>=1),
 name_en varchar(160) NOT NULL, name_fr varchar(160) NOT NULL, metric_keys text[] NOT NULL CHECK(cardinality(metric_keys) BETWEEN 1 AND 20),
 max_period_days int NOT NULL CHECK(max_period_days BETWEEN 1 AND 800), required_permission varchar(40) NOT NULL CHECK(required_permission ~ '^m28:[a-z_]+$'),
 status varchar(10) NOT NULL CHECK(status IN ('DRAFT','APPROVED','RETIRED','REJECTED')),
 created_by uuid NOT NULL, approved_by uuid, approved_at timestamptz, version bigint NOT NULL CHECK(version>=1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,report_key,version_no),
 CONSTRAINT report_approver_independent CHECK(approved_by IS NULL OR approved_by<>created_by),
 CONSTRAINT report_approved CHECK(status NOT IN ('APPROVED','RETIRED') OR approved_by IS NOT NULL));
CREATE UNIQUE INDEX one_approved_report ON synqoria_analytics.report_definitions (cell_id,facility_id,report_key) WHERE status='APPROVED';

CREATE TABLE synqoria_analytics.report_runs(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, report_id uuid NOT NULL,
 period_start date NOT NULL, period_end date NOT NULL, trigger varchar(10) NOT NULL CHECK(trigger IN ('MANUAL','SCHEDULE')),
 status varchar(10) NOT NULL CHECK(status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
 requested_by uuid NOT NULL, result jsonb, error_code varchar(60),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), started_at timestamptz, finished_at timestamptz, version bigint NOT NULL CHECK(version>=1),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,report_id) REFERENCES synqoria_analytics.report_definitions(cell_id,facility_id,id),
 CHECK(period_end>=period_start),
 CONSTRAINT run_result_state CHECK((status='SUCCEEDED')=(result IS NOT NULL) AND (status='FAILED')=(error_code IS NOT NULL)));
CREATE INDEX runs_recent ON synqoria_analytics.report_runs (cell_id,facility_id,created_at DESC);

CREATE TABLE synqoria_analytics.schedules(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, report_id uuid NOT NULL,
 every_minutes int NOT NULL CHECK(every_minutes BETWEEN 15 AND 44640), lookback_days int NOT NULL CHECK(lookback_days BETWEEN 1 AND 800),
 next_run_at timestamptz NOT NULL, enabled boolean NOT NULL DEFAULT true, created_by uuid NOT NULL,
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,report_id),
 FOREIGN KEY(cell_id,facility_id,report_id) REFERENCES synqoria_analytics.report_definitions(cell_id,facility_id,id));

CREATE TABLE synqoria_analytics.exports(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, run_id uuid NOT NULL,
 format varchar(4) NOT NULL CHECK(format IN ('CSV')),                       -- PDF/XLSX adapters are not implemented and are refused, never faked
 status varchar(10) NOT NULL CHECK(status IN ('REQUESTED','READY','REJECTED','EXPIRED')),
 purpose varchar(300) NOT NULL CHECK(length(btrim(purpose))>0),
 requested_by uuid NOT NULL, approved_by uuid, approved_at timestamptz,
 artifact bytea, artifact_sha256 char(64), artifact_bytes int, expires_at timestamptz, watermark varchar(300),
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,run_id) REFERENCES synqoria_analytics.report_runs(cell_id,facility_id,id),
 CONSTRAINT export_approver_independent CHECK(approved_by IS NULL OR approved_by<>requested_by),
 CONSTRAINT ready_has_artifact CHECK((status IN ('READY','EXPIRED'))=(approved_by IS NOT NULL AND artifact_sha256 IS NOT NULL AND expires_at IS NOT NULL AND watermark IS NOT NULL)),
 CONSTRAINT expired_purged CHECK(status<>'EXPIRED' OR artifact IS NULL),
 CONSTRAINT ready_has_bytes CHECK(status<>'READY' OR (artifact IS NOT NULL AND artifact_bytes BETWEEN 1 AND 5242880)));
CREATE TABLE synqoria_analytics.export_downloads(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, export_id uuid NOT NULL, actor_id uuid NOT NULL,
 downloaded_at timestamptz NOT NULL DEFAULT clock_timestamp(), artifact_sha256 char(64) NOT NULL,
 FOREIGN KEY(cell_id,facility_id,export_id) REFERENCES synqoria_analytics.exports(cell_id,facility_id,id));

-- ------------------------------------------------------------------ idempotency, audit, outbox
CREATE TABLE synqoria_analytics.command_idempotency(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, actor_id uuid NOT NULL, command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'), response jsonb, http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id), CHECK((response IS NULL)=(http_status IS NULL)));
CREATE TABLE synqoria_analytics.audit_events(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, aggregate_type varchar(24) NOT NULL, aggregate_id uuid NOT NULL,
 action varchar(48) NOT NULL, from_status varchar(20), to_status varchar(20) NOT NULL, aggregate_version bigint NOT NULL CHECK(aggregate_version>=1),
 actor_id uuid NOT NULL, command_id uuid NOT NULL, details jsonb NOT NULL CHECK(jsonb_typeof(details)='object'),
 digest char(64) NOT NULL CHECK(digest ~ '^[0-9a-f]{64}$'), occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id));
CREATE INDEX audit_aggregate ON synqoria_analytics.audit_events (cell_id,facility_id,aggregate_type,aggregate_id,occurred_at,event_id);
CREATE TABLE synqoria_analytics.outbox(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, event_type varchar(64) NOT NULL, aggregate_id uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_analytics.audit_events(cell_id,facility_id,event_id));
CREATE INDEX outbox_unpublished ON synqoria_analytics.outbox (created_at,event_id) WHERE published_at IS NULL;

-- ------------------------------------------------------------------ immutability and invariants
CREATE FUNCTION synqoria_analytics.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN RAISE EXCEPTION 'm28_immutable_record: % on %.% is not permitted',TG_OP,TG_TABLE_SCHEMA,TG_TABLE_NAME USING ERRCODE='integrity_constraint_violation'; END $t$;
DO $imm$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['audit_events','export_downloads'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON synqoria_analytics.%I FOR EACH ROW EXECUTE FUNCTION synqoria_analytics.forbid_mutation()',rel||'_immutable',rel);
  EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON synqoria_analytics.%I FOR EACH STATEMENT EXECUTE FUNCTION synqoria_analytics.forbid_mutation()',rel||'_no_truncate',rel);
 END LOOP;
END $imm$;
-- received events are evidence: the payload and identity of an ingested event never change, only its handling status
CREATE FUNCTION synqoria_analytics.protect_event() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR NEW.payload IS DISTINCT FROM OLD.payload OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash OR NEW.source_event_id IS DISTINCT FROM OLD.source_event_id
    OR NEW.entity_key IS DISTINCT FROM OLD.entity_key OR NEW.source_version IS DISTINCT FROM OLD.source_version OR NEW.occurred_at IS DISTINCT FROM OLD.occurred_at OR NEW.source_id IS DISTINCT FROM OLD.source_id
    OR (OLD.status IN ('APPLIED','SUPERSEDED','DISCARDED') AND NEW.status IS DISTINCT FROM OLD.status AND NOT (OLD.status='APPLIED' AND NEW.status='SUPERSEDED'))
 THEN RAISE EXCEPTION 'm28_immutable_record: ingested events are evidence' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER events_protect BEFORE UPDATE OR DELETE ON synqoria_analytics.ingest_events FOR EACH ROW EXECUTE FUNCTION synqoria_analytics.protect_event();
-- approved definitions are immutable (new content = new version) and follow a fixed state machine
CREATE FUNCTION synqoria_analytics.protect_definition() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm28_immutable_record: definitions are never deleted' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF OLD.status<>'DRAFT' AND to_jsonb(NEW)-'status'-'approved_by'-'approved_at'-'version' IS DISTINCT FROM to_jsonb(OLD)-'status'-'approved_by'-'approved_at'-'version'
 THEN RAISE EXCEPTION 'm28_immutable_record: approved definition content is immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.status<>OLD.status AND NOT ((OLD.status='DRAFT' AND NEW.status IN ('APPROVED','REJECTED')) OR (OLD.status='APPROVED' AND NEW.status='RETIRED'))
 THEN RAISE EXCEPTION 'm28_invalid_transition: % -> %',OLD.status,NEW.status USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER metric_defs_protect BEFORE UPDATE OR DELETE ON synqoria_analytics.metric_definitions FOR EACH ROW EXECUTE FUNCTION synqoria_analytics.protect_definition();
CREATE TRIGGER report_defs_protect BEFORE UPDATE OR DELETE ON synqoria_analytics.report_definitions FOR EACH ROW EXECUTE FUNCTION synqoria_analytics.protect_definition();
-- a finished run is a fixed record; an export artifact, once approved, never changes except being purged at expiry
CREATE FUNCTION synqoria_analytics.protect_run() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR (OLD.status IN ('SUCCEEDED','FAILED') AND NEW IS DISTINCT FROM OLD)
    OR NEW.report_id IS DISTINCT FROM OLD.report_id OR NEW.period_start IS DISTINCT FROM OLD.period_start OR NEW.period_end IS DISTINCT FROM OLD.period_end OR NEW.requested_by IS DISTINCT FROM OLD.requested_by
 THEN RAISE EXCEPTION 'm28_immutable_record: report runs are fixed once finished' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER runs_protect BEFORE UPDATE OR DELETE ON synqoria_analytics.report_runs FOR EACH ROW EXECUTE FUNCTION synqoria_analytics.protect_run();
CREATE FUNCTION synqoria_analytics.protect_export() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR NEW.run_id IS DISTINCT FROM OLD.run_id OR NEW.requested_by IS DISTINCT FROM OLD.requested_by OR NEW.purpose IS DISTINCT FROM OLD.purpose
    OR (OLD.status='READY' AND (NEW.artifact_sha256 IS DISTINCT FROM OLD.artifact_sha256 OR NEW.watermark IS DISTINCT FROM OLD.watermark OR NEW.approved_by IS DISTINCT FROM OLD.approved_by
        OR (NEW.status='READY' AND NEW.artifact IS DISTINCT FROM OLD.artifact)))
    OR (OLD.status IN ('REJECTED','EXPIRED') AND NEW.status IS DISTINCT FROM OLD.status)
 THEN RAISE EXCEPTION 'm28_immutable_record: export evidence is fixed' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER exports_protect BEFORE UPDATE OR DELETE ON synqoria_analytics.exports FOR EACH ROW EXECUTE FUNCTION synqoria_analytics.protect_export();

-- ------------------------------------------------------------------ grants and forced RLS
GRANT USAGE ON SCHEMA synqoria_analytics TO synqoria_analytics_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_analytics.sources, synqoria_analytics.ingest_events, synqoria_analytics.facts, synqoria_analytics.metric_definitions,
 synqoria_analytics.metric_values, synqoria_analytics.report_definitions, synqoria_analytics.report_runs, synqoria_analytics.schedules, synqoria_analytics.exports,
 synqoria_analytics.command_idempotency TO synqoria_analytics_runtime;
GRANT SELECT,INSERT ON synqoria_analytics.export_downloads, synqoria_analytics.audit_events, synqoria_analytics.outbox TO synqoria_analytics_runtime;
-- projections are rebuilt by replacing rows; metric values are recomputed in place; nothing else is ever deleted
GRANT DELETE ON synqoria_analytics.facts, synqoria_analytics.metric_values TO synqoria_analytics_runtime;

DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['sources','ingest_events','facts','metric_definitions','metric_values','report_definitions','report_runs','schedules','exports','export_downloads',
  'command_idempotency','audit_events','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_analytics.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_analytics.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('CREATE POLICY analytics_scoped_access ON synqoria_analytics.%I FOR ALL TO synqoria_analytics_runtime '||
   'USING (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) '||
   'WITH CHECK (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;
-- Cross-facility reads are possible ONLY inside the same hospital cell, ONLY on published aggregates (never events or facts),
-- and only with the verified cell_aggregate_read grant. Never across cells.
CREATE POLICY analytics_cell_aggregates ON synqoria_analytics.metric_values FOR SELECT TO synqoria_analytics_runtime
 USING (cell_id = NULLIF(current_setting('synqoria.cell',true),'')::uuid AND current_setting('synqoria.analytics_cell',true)='on' AND state='OK');
COMMIT;
