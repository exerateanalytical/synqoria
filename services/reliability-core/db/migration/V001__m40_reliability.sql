-- SYNQORIA M40 Reliability, backup, recovery and operational telemetry (hospital-cell scoped).
-- Apply with a separate DATABASE MIGRATION role, never the runtime login.
-- Provision synqoria_reliability_runtime (LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEROLE NOCREATEDB NOREPLICATION) first.
-- Boundary: this schema records service health, incidents, backup METADATA and its verification, recovery runs and redacted telemetry.
-- It holds no clinical data and no key material (only key REFERENCES). A backup is never VERIFIED because its creation succeeded; recovery
-- states advance only on recorded evidence; RPO/RTO rows exist only for recoveries that were actually completed.
BEGIN;
DO $guard$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_reliability_runtime' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication AND NOT rolinherit)
 THEN RAISE EXCEPTION 'missing independent restricted synqoria_reliability_runtime'; END IF;
END $guard$;
CREATE SCHEMA IF NOT EXISTS synqoria_reliability;
REVOKE ALL ON SCHEMA synqoria_reliability FROM PUBLIC;

-- ---------------------------------------------------------------- B01 service contracts, dependencies, signals
CREATE TABLE synqoria_reliability.services(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 service_key varchar(48) NOT NULL CHECK(service_key ~ '^[a-z][a-z0-9-]{2,47}$'), name varchar(120) NOT NULL CHECK(length(btrim(name))>0),
 tier varchar(10) NOT NULL CHECK(tier IN ('CRITICAL','IMPORTANT','SUPPORT')), owner_ref varchar(120) NOT NULL CHECK(length(btrim(owner_ref))>0),
 recovery_boundary varchar(200) NOT NULL CHECK(length(btrim(recovery_boundary))>0),                  -- what is restored together with this service
 slo_target_pct numeric(6,3) NOT NULL CHECK(slo_target_pct>0 AND slo_target_pct<=100),
 version bigint NOT NULL CHECK(version>=1), created_by uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,service_key));
CREATE TABLE synqoria_reliability.service_dependencies(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, service_id uuid NOT NULL, depends_on uuid NOT NULL CHECK(depends_on<>service_id),
 kind varchar(4) NOT NULL CHECK(kind IN ('HARD','SOFT')), UNIQUE(cell_id,facility_id,service_id,depends_on),
 FOREIGN KEY(cell_id,facility_id,service_id) REFERENCES synqoria_reliability.services(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,depends_on) REFERENCES synqoria_reliability.services(cell_id,facility_id,id));
CREATE TABLE synqoria_reliability.health_signals(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, service_id uuid NOT NULL,
 signal_kind varchar(16) NOT NULL CHECK(signal_kind IN ('READINESS','LATENCY_P95_MS','ERROR_RATE_PCT','SATURATION_PCT','BACKLOG')),
 value numeric(14,3) NOT NULL CHECK(value>=0), state varchar(4) NOT NULL CHECK(state IN ('OK','WARN','CRIT')),
 observed_at timestamptz NOT NULL, trace_id varchar(64), reported_by uuid NOT NULL, recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,service_id) REFERENCES synqoria_reliability.services(cell_id,facility_id,id));
CREATE INDEX signals_latest ON synqoria_reliability.health_signals (cell_id,facility_id,service_id,signal_kind,observed_at DESC);

-- ---------------------------------------------------------------- B02 redacted telemetry and audited access
CREATE TABLE synqoria_reliability.telemetry_events(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, service_id uuid,
 correlation_id varchar(64) NOT NULL CHECK(correlation_id ~ '^[A-Za-z0-9._-]{8,64}$'), trace_id varchar(64), span_id varchar(32),
 level varchar(5) NOT NULL CHECK(level IN ('DEBUG','INFO','WARN','ERROR')), event_type varchar(64) NOT NULL CHECK(event_type ~ '^[a-z][a-z0-9_.-]{2,63}$'),
 message varchar(400) NOT NULL, attributes jsonb NOT NULL CHECK(jsonb_typeof(attributes)='object' AND octet_length(attributes::text)<=4096),
 redactions int NOT NULL CHECK(redactions>=0), occurred_at timestamptz NOT NULL, recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,service_id) REFERENCES synqoria_reliability.services(cell_id,facility_id,id));
CREATE INDEX telemetry_corr ON synqoria_reliability.telemetry_events (cell_id,facility_id,correlation_id,occurred_at);
CREATE INDEX telemetry_time ON synqoria_reliability.telemetry_events (cell_id,facility_id,occurred_at DESC);
CREATE TABLE synqoria_reliability.telemetry_access_log(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, actor_id uuid NOT NULL, query_summary jsonb NOT NULL CHECK(jsonb_typeof(query_summary)='object'),
 returned int NOT NULL CHECK(returned>=0), accessed_at timestamptz NOT NULL DEFAULT clock_timestamp());

-- ---------------------------------------------------------------- B03 incidents
CREATE SEQUENCE synqoria_reliability.incident_number;
CREATE TABLE synqoria_reliability.incidents(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, number bigint NOT NULL,
 title varchar(200) NOT NULL CHECK(length(btrim(title))>0), severity varchar(4) NOT NULL CHECK(severity IN ('SEV1','SEV2','SEV3','SEV4')),
 status varchar(12) NOT NULL CHECK(status IN ('OPEN','ACKNOWLEDGED','MITIGATING','MITIGATED','RESOLVED')),
 service_id uuid, impact varchar(500) NOT NULL CHECK(length(btrim(impact))>0), owner_id uuid, escalation_level int NOT NULL DEFAULT 0 CHECK(escalation_level BETWEEN 0 AND 3),
 created_by uuid NOT NULL, declared_at timestamptz NOT NULL, acknowledged_by uuid, acknowledged_at timestamptz, mitigated_at timestamptz,
 verified_by uuid, verification_evidence varchar(300), resolved_at timestamptz, version bigint NOT NULL CHECK(version>=1),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,number),
 FOREIGN KEY(cell_id,facility_id,service_id) REFERENCES synqoria_reliability.services(cell_id,facility_id,id),
 CONSTRAINT resolved_needs_verification CHECK(status<>'RESOLVED' OR (verified_by IS NOT NULL AND verification_evidence IS NOT NULL AND resolved_at IS NOT NULL)),
 CONSTRAINT verifier_independent CHECK(verified_by IS NULL OR owner_id IS NULL OR verified_by<>owner_id),
 CONSTRAINT ack_has_actor CHECK(status='OPEN' OR (acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL)));
CREATE TABLE synqoria_reliability.incident_timeline(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, incident_id uuid NOT NULL,
 kind varchar(12) NOT NULL CHECK(kind IN ('DECLARED','ACK','ASSIGN','NOTE','ACTION','ESCALATION','STATUS','VERIFIED')),
 text varchar(500) NOT NULL CHECK(length(btrim(text))>0), actor_id uuid NOT NULL, at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,incident_id) REFERENCES synqoria_reliability.incidents(cell_id,facility_id,id));
CREATE INDEX timeline_incident ON synqoria_reliability.incident_timeline (cell_id,facility_id,incident_id,at,id);

-- ---------------------------------------------------------------- B04/B05 backup catalogue and verification
CREATE TABLE synqoria_reliability.backups(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 kind varchar(12) NOT NULL CHECK(kind IN ('FULL','INCREMENTAL','WAL_ARCHIVE')), snapshot_id varchar(120) NOT NULL CHECK(length(btrim(snapshot_id))>0),
 database_ref varchar(120) NOT NULL, started_at timestamptz NOT NULL, finished_at timestamptz NOT NULL,
 wal_start bigint NOT NULL CHECK(wal_start>=0), wal_end bigint NOT NULL, object_refs text[] NOT NULL CHECK(cardinality(object_refs)<=100),
 size_bytes bigint NOT NULL CHECK(size_bytes>0), digest char(64) NOT NULL CHECK(digest ~ '^[0-9a-f]{64}$'),
 key_ref varchar(120) NOT NULL CHECK(length(btrim(key_ref))>0), retention_until timestamptz NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('RECORDED','VERIFIED','VERIFICATION_FAILED')), recorded_by uuid NOT NULL, verified_by uuid,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,snapshot_id),
 CONSTRAINT wal_order CHECK(wal_end>=wal_start), CONSTRAINT time_order CHECK(finished_at>=started_at), CONSTRAINT retention_after CHECK(retention_until>finished_at),
 CONSTRAINT verifier_independent CHECK(verified_by IS NULL OR verified_by<>recorded_by),
 CONSTRAINT verified_has_verifier CHECK(status='RECORDED' OR verified_by IS NOT NULL));
CREATE TABLE synqoria_reliability.backup_verifications(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, backup_id uuid NOT NULL,
 check_key varchar(20) NOT NULL CHECK(check_key IN ('INVENTORY','INTEGRITY','ENCRYPTION_KEY','COMPLETENESS','RESTORE_READINESS')),
 result varchar(4) NOT NULL CHECK(result IN ('PASS','FAIL')), evidence_ref varchar(160) NOT NULL CHECK(length(btrim(evidence_ref))>0), observed_digest char(64),
 verified_by uuid NOT NULL, at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,backup_id,check_key), FOREIGN KEY(cell_id,facility_id,backup_id) REFERENCES synqoria_reliability.backups(cell_id,facility_id,id));

-- ---------------------------------------------------------------- B06-B08 recovery orchestration, validation and reopening
CREATE TABLE synqoria_reliability.recoveries(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, incident_id uuid,
 scenario varchar(24) NOT NULL CHECK(scenario IN ('DATABASE_FAILURE','APPLICATION_FAILURE','INFRASTRUCTURE_LOSS','CONNECTIVITY_INTERRUPTION','ISOLATED_CELL_FAILURE')),
 exercise boolean NOT NULL, base_backup_id uuid NOT NULL, target_lsn bigint NOT NULL CHECK(target_lsn>=0), target_time timestamptz,
 outage_started_at timestamptz NOT NULL,
 state varchar(20) NOT NULL CHECK(state IN ('PLANNED','AUTHORIZED','FENCED','DB_RESTORED','OBJECTS_RECONCILED','AUDIT_RECOVERED','EFFECTS_RECONCILED','VALIDATING','REOPENED','ABORTED')),
 planned_by uuid NOT NULL, authorized_by uuid, abort_reason varchar(300),
 fence_evidence varchar(160), restored_lsn bigint, restore_evidence varchar(160), objects_evidence varchar(160), audit_chain_digest char(64), effects_evidence varchar(160),
 reopen_approved_by uuid, reopen_scope text[], reopened_at timestamptz,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), FOREIGN KEY(cell_id,facility_id,base_backup_id) REFERENCES synqoria_reliability.backups(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,incident_id) REFERENCES synqoria_reliability.incidents(cell_id,facility_id,id),
 CONSTRAINT authorizer_independent CHECK(authorized_by IS NULL OR authorized_by<>planned_by),
 CONSTRAINT reopener_independent CHECK(reopen_approved_by IS NULL OR reopen_approved_by<>planned_by),
 CONSTRAINT reopened_complete CHECK(state<>'REOPENED' OR (reopen_approved_by IS NOT NULL AND reopened_at IS NOT NULL AND cardinality(reopen_scope)>=1
   AND restored_lsn IS NOT NULL AND audit_chain_digest IS NOT NULL AND fence_evidence IS NOT NULL AND authorized_by IS NOT NULL)));
CREATE UNIQUE INDEX one_open_real_recovery ON synqoria_reliability.recoveries (cell_id,facility_id) WHERE exercise=false AND state NOT IN ('REOPENED','ABORTED');
CREATE TABLE synqoria_reliability.recovery_steps(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, recovery_id uuid NOT NULL, step varchar(24) NOT NULL, evidence_ref varchar(160), detail jsonb NOT NULL,
 actor_id uuid NOT NULL, at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,recovery_id) REFERENCES synqoria_reliability.recoveries(cell_id,facility_id,id));
CREATE TABLE synqoria_reliability.recovery_validations(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, recovery_id uuid NOT NULL,
 item_key varchar(28) NOT NULL CHECK(item_key IN ('SMOKE_TESTS','IDENTITY_CONTINUITY','CLINICAL_RECONCILIATION','FINANCIAL_RECONCILIATION','AUDIT_CONTINUITY','EGRESS_STATE')),
 result varchar(4) NOT NULL CHECK(result IN ('PASS','FAIL')), evidence_ref varchar(160) NOT NULL CHECK(length(btrim(evidence_ref))>0), residual_risk varchar(300),
 validated_by uuid NOT NULL, at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,recovery_id,item_key), FOREIGN KEY(cell_id,facility_id,recovery_id) REFERENCES synqoria_reliability.recoveries(cell_id,facility_id,id));
-- measured, never promised: a row exists only for a recovery that really reopened
CREATE TABLE synqoria_reliability.rpo_rto_measurements(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, recovery_id uuid NOT NULL, source varchar(8) NOT NULL CHECK(source IN ('EXERCISE','REAL')),
 rto_seconds bigint NOT NULL CHECK(rto_seconds>=0), rpo_seconds bigint CHECK(rpo_seconds IS NULL OR rpo_seconds>=0), measured_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,recovery_id), FOREIGN KEY(cell_id,facility_id,recovery_id) REFERENCES synqoria_reliability.recoveries(cell_id,facility_id,id));

-- ---------------------------------------------------------------- idempotency, audit, outbox
CREATE TABLE synqoria_reliability.command_idempotency(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, actor_id uuid NOT NULL, command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'), response jsonb, http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id), CHECK((response IS NULL)=(http_status IS NULL)));
CREATE TABLE synqoria_reliability.audit_events(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, aggregate_type varchar(24) NOT NULL, aggregate_id uuid NOT NULL,
 action varchar(48) NOT NULL, from_status varchar(20), to_status varchar(20) NOT NULL, aggregate_version bigint NOT NULL CHECK(aggregate_version>=1),
 actor_id uuid NOT NULL, command_id uuid NOT NULL, details jsonb NOT NULL CHECK(jsonb_typeof(details)='object'),
 digest char(64) NOT NULL CHECK(digest ~ '^[0-9a-f]{64}$'), occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id));
CREATE INDEX audit_aggregate ON synqoria_reliability.audit_events (cell_id,facility_id,aggregate_type,aggregate_id,occurred_at,event_id);
CREATE TABLE synqoria_reliability.outbox(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, event_type varchar(64) NOT NULL, aggregate_id uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_reliability.audit_events(cell_id,facility_id,event_id));
CREATE INDEX outbox_unpublished ON synqoria_reliability.outbox (created_at,event_id) WHERE published_at IS NULL;

-- ---------------------------------------------------------------- invariants
CREATE FUNCTION synqoria_reliability.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN RAISE EXCEPTION 'm40_immutable_record: % on %.% is not permitted',TG_OP,TG_TABLE_SCHEMA,TG_TABLE_NAME USING ERRCODE='integrity_constraint_violation'; END $t$;
DO $imm$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['audit_events','health_signals','telemetry_events','telemetry_access_log','incident_timeline','backup_verifications','recovery_steps','recovery_validations','rpo_rto_measurements','service_dependencies'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON synqoria_reliability.%I FOR EACH ROW EXECUTE FUNCTION synqoria_reliability.forbid_mutation()',rel||'_immutable',rel);
  EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON synqoria_reliability.%I FOR EACH STATEMENT EXECUTE FUNCTION synqoria_reliability.forbid_mutation()',rel||'_no_truncate',rel);
 END LOOP;
END $imm$;
-- a backup's recorded facts never change; only status moves RECORDED -> VERIFIED/VERIFICATION_FAILED, and only with all five passing checks for VERIFIED
CREATE FUNCTION synqoria_reliability.protect_backup() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR OLD.status<>'RECORDED' OR to_jsonb(NEW)-'status'-'verified_by'-'version' IS DISTINCT FROM to_jsonb(OLD)-'status'-'verified_by'-'version'
 THEN RAISE EXCEPTION 'm40_immutable_record: backup facts' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.status='VERIFIED' AND (SELECT count(*) FROM synqoria_reliability.backup_verifications v WHERE v.backup_id=NEW.id AND v.result='PASS')<>5
 THEN RAISE EXCEPTION 'm40_not_verified: five passing checks are required' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER backups_protect BEFORE UPDATE OR DELETE ON synqoria_reliability.backups FOR EACH ROW EXECUTE FUNCTION synqoria_reliability.protect_backup();
-- incident states move forward only; identity and declaration facts never change
CREATE FUNCTION synqoria_reliability.protect_incident() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR OLD.status='RESOLVED' OR NEW.number IS DISTINCT FROM OLD.number OR NEW.created_by IS DISTINCT FROM OLD.created_by OR NEW.declared_at IS DISTINCT FROM OLD.declared_at
    OR (NEW.status<>OLD.status AND NOT ((OLD.status='OPEN' AND NEW.status='ACKNOWLEDGED') OR (OLD.status='ACKNOWLEDGED' AND NEW.status IN ('MITIGATING','MITIGATED'))
        OR (OLD.status='MITIGATING' AND NEW.status='MITIGATED') OR (OLD.status='MITIGATED' AND NEW.status='RESOLVED')))
    OR NEW.escalation_level<OLD.escalation_level
 THEN RAISE EXCEPTION 'm40_invalid_transition: incident' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER incidents_protect BEFORE UPDATE OR DELETE ON synqoria_reliability.incidents FOR EACH ROW EXECUTE FUNCTION synqoria_reliability.protect_incident();
-- recovery runs advance along a fixed line; REOPENED and ABORTED are final; reopening requires all six validation items passing
CREATE FUNCTION synqoria_reliability.protect_recovery() RETURNS trigger LANGUAGE plpgsql AS $t$
DECLARE ord text[]:=ARRAY['PLANNED','AUTHORIZED','FENCED','DB_RESTORED','OBJECTS_RECONCILED','AUDIT_RECOVERED','EFFECTS_RECONCILED','VALIDATING','REOPENED'];
BEGIN
 IF TG_OP='DELETE' OR OLD.state IN ('REOPENED','ABORTED') OR NEW.base_backup_id IS DISTINCT FROM OLD.base_backup_id OR NEW.target_lsn IS DISTINCT FROM OLD.target_lsn OR NEW.planned_by IS DISTINCT FROM OLD.planned_by
    OR NEW.exercise IS DISTINCT FROM OLD.exercise OR NEW.outage_started_at IS DISTINCT FROM OLD.outage_started_at
    OR (NEW.state<>OLD.state AND NEW.state<>'ABORTED' AND array_position(ord,NEW.state)<>array_position(ord,OLD.state)+1)
 THEN RAISE EXCEPTION 'm40_invalid_transition: recovery' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.state='REOPENED' AND (SELECT count(*) FROM synqoria_reliability.recovery_validations v WHERE v.recovery_id=NEW.id AND v.result='PASS')<>6
 THEN RAISE EXCEPTION 'm40_validation_incomplete: six passing validation items are required' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER recoveries_protect BEFORE UPDATE OR DELETE ON synqoria_reliability.recoveries FOR EACH ROW EXECUTE FUNCTION synqoria_reliability.protect_recovery();
CREATE FUNCTION synqoria_reliability.protect_service() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN IF TG_OP='DELETE' OR NEW.service_key IS DISTINCT FROM OLD.service_key THEN RAISE EXCEPTION 'm40_immutable_record: service identity' USING ERRCODE='integrity_constraint_violation'; END IF; RETURN NEW; END $t$;
CREATE TRIGGER services_protect BEFORE UPDATE OR DELETE ON synqoria_reliability.services FOR EACH ROW EXECUTE FUNCTION synqoria_reliability.protect_service();

-- ---------------------------------------------------------------- grants and forced RLS
GRANT USAGE ON SCHEMA synqoria_reliability TO synqoria_reliability_runtime;
GRANT USAGE,SELECT ON SEQUENCE synqoria_reliability.incident_number TO synqoria_reliability_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_reliability.services, synqoria_reliability.incidents, synqoria_reliability.backups, synqoria_reliability.recoveries, synqoria_reliability.command_idempotency TO synqoria_reliability_runtime;
GRANT SELECT,INSERT ON synqoria_reliability.service_dependencies, synqoria_reliability.health_signals, synqoria_reliability.telemetry_events, synqoria_reliability.telemetry_access_log, synqoria_reliability.incident_timeline,
 synqoria_reliability.backup_verifications, synqoria_reliability.recovery_steps, synqoria_reliability.recovery_validations, synqoria_reliability.rpo_rto_measurements, synqoria_reliability.audit_events, synqoria_reliability.outbox TO synqoria_reliability_runtime;
DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['services','service_dependencies','health_signals','telemetry_events','telemetry_access_log','incidents','incident_timeline','backups','backup_verifications','recoveries','recovery_steps','recovery_validations','rpo_rto_measurements','command_idempotency','audit_events','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_reliability.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_reliability.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('CREATE POLICY reliability_scoped_access ON synqoria_reliability.%I FOR ALL TO synqoria_reliability_runtime '||
   'USING (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) '||
   'WITH CHECK (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;
COMMIT;
