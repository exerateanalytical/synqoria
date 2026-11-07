-- SYNQORIA V013 - M23 preventive maintenance, calibration and servicing calendar (M23-owned tables only).
-- Extends the existing synqoria_operations schema; it does NOT recreate assets, work orders, evidence or workforce grants.
-- Apply as the migration owner after V012. The runtime role gets least privilege; every table is FORCE RLS on cell+facility.
-- A maintenance plan is a hospital-internal schedule. It is not manufacturer certification, and nothing here posts to a ledger.
BEGIN;

-- 1. Generic immutability for versioned, maker-checker rows: only decision / decided_by / decided_at may change,
--    and only along PENDING -> APPROVED|REJECTED and APPROVED -> SUPERSEDED. Rows are never deleted.
CREATE FUNCTION synqoria_operations.m23_versioned_row_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm23_version_history_immutable' USING ERRCODE='23000'; END IF;
 IF (to_jsonb(NEW)-'decision'-'decided_by'-'decided_at') IS DISTINCT FROM (to_jsonb(OLD)-'decision'-'decided_by'-'decided_at') THEN
  RAISE EXCEPTION 'm23_version_content_immutable' USING ERRCODE='23000';
 END IF;
 IF NOT ((OLD.decision='PENDING' AND NEW.decision IN('APPROVED','REJECTED') AND NEW.decided_by IS NOT NULL)
      OR (OLD.decision='APPROVED' AND NEW.decision='SUPERSEDED' AND NEW.decided_by IS NOT DISTINCT FROM OLD.decided_by
          AND NEW.decided_at IS NOT DISTINCT FROM OLD.decided_at)
      OR (NEW.decision=OLD.decision AND NEW.decided_by IS NOT DISTINCT FROM OLD.decided_by AND NEW.decided_at IS NOT DISTINCT FROM OLD.decided_at)) THEN
  RAISE EXCEPTION 'm23_version_decision_transition_denied' USING ERRCODE='23000';
 END IF;
 RETURN NEW;
END $fn$;

-- 2. Facility policy per safety class (versioned, maker-checker). Absent policy => explicit code defaults (MaintenanceRules).
CREATE TABLE synqoria_operations.pm_policy_versions(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 safety_class varchar(8) NOT NULL CHECK(safety_class IN('LOW','MEDIUM','HIGH','CRITICAL')),
 version integer NOT NULL CHECK(version>=1),
 interval_days integer NOT NULL CHECK(interval_days BETWEEN 1 AND 730),
 calibration_interval_days integer CHECK(calibration_interval_days IS NULL OR calibration_interval_days BETWEEN 1 AND 1095),
 grace_hours integer NOT NULL CHECK(grace_hours BETWEEN 0 AND 720),
 escalate_after_hours integer NOT NULL CHECK(escalate_after_hours BETWEEN 1 AND 2160),
 proposed_by uuid NOT NULL,
 proposed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 decision varchar(12) NOT NULL DEFAULT 'PENDING' CHECK(decision IN('PENDING','APPROVED','REJECTED','SUPERSEDED')),
 decided_by uuid,
 decided_at timestamptz,
 PRIMARY KEY(cell_id,facility_id,safety_class,version),
 CONSTRAINT pm_policy_decided_consistent CHECK((decision='PENDING')=(decided_by IS NULL AND decided_at IS NULL)),
 CONSTRAINT pm_policy_maker_checker CHECK(decided_by IS NULL OR decided_by<>proposed_by)
);
CREATE UNIQUE INDEX pm_one_approved_policy ON synqoria_operations.pm_policy_versions(cell_id,facility_id,safety_class) WHERE decision='APPROVED';
CREATE UNIQUE INDEX pm_one_pending_policy ON synqoria_operations.pm_policy_versions(cell_id,facility_id,safety_class) WHERE decision='PENDING';
CREATE TRIGGER pm_policy_guard BEFORE UPDATE OR DELETE ON synqoria_operations.pm_policy_versions
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.m23_versioned_row_guard();

-- 3. Plans (one live plan per asset) and immutable approved versions.
CREATE TABLE synqoria_operations.pm_plans(
 plan_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 asset_ref uuid NOT NULL,
 created_by uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 retired_at timestamptz,
 retired_by uuid,
 UNIQUE(cell_id,facility_id,plan_id),
 FOREIGN KEY(cell_id,facility_id,asset_ref) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 CONSTRAINT pm_plan_retired_consistent CHECK((retired_at IS NULL)=(retired_by IS NULL))
);
CREATE UNIQUE INDEX pm_one_live_plan_per_asset ON synqoria_operations.pm_plans(cell_id,facility_id,asset_ref) WHERE retired_at IS NULL;

CREATE TABLE synqoria_operations.pm_plan_versions(
 plan_id uuid NOT NULL,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 version integer NOT NULL CHECK(version>=1),
 service_type varchar(28) NOT NULL CHECK(service_type IN('PREVENTIVE','CALIBRATION','PREVENTIVE_AND_CALIBRATION')),
 safety_class varchar(8) NOT NULL CHECK(safety_class IN('LOW','MEDIUM','HIGH','CRITICAL')),
 interval_days integer NOT NULL CHECK(interval_days BETWEEN 1 AND 730),
 calibration_interval_days integer CHECK(calibration_interval_days IS NULL OR calibration_interval_days BETWEEN 1 AND 1095),
 timezone varchar(64) NOT NULL CHECK(timezone ~ '^[A-Za-z0-9_+/-]{1,64}$'),
 anchor_date date NOT NULL,
 local_time time NOT NULL,
 calendar_rule varchar(16) NOT NULL CHECK(calendar_rule IN('EXACT_DAY','NEXT_WEEKDAY')),
 grace_hours integer NOT NULL CHECK(grace_hours BETWEEN 0 AND 720),
 escalate_after_hours integer NOT NULL CHECK(escalate_after_hours BETWEEN 1 AND 2160),
 content_digest char(64) NOT NULL CHECK(content_digest ~ '^[0-9a-f]{64}$'),
 proposed_by uuid NOT NULL,
 proposed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 decision varchar(12) NOT NULL DEFAULT 'PENDING' CHECK(decision IN('PENDING','APPROVED','REJECTED','SUPERSEDED')),
 decided_by uuid,
 decided_at timestamptz,
 PRIMARY KEY(plan_id,version),
 FOREIGN KEY(cell_id,facility_id,plan_id) REFERENCES synqoria_operations.pm_plans(cell_id,facility_id,plan_id),
 CONSTRAINT pm_version_decided_consistent CHECK((decision='PENDING')=(decided_by IS NULL AND decided_at IS NULL)),
 CONSTRAINT pm_version_maker_checker CHECK(decided_by IS NULL OR decided_by<>proposed_by),
 CONSTRAINT pm_version_calibration_interval CHECK(service_type='PREVENTIVE' OR calibration_interval_days IS NOT NULL)
);
CREATE UNIQUE INDEX pm_one_pending_version ON synqoria_operations.pm_plan_versions(plan_id) WHERE decision='PENDING';
CREATE UNIQUE INDEX pm_one_approved_version ON synqoria_operations.pm_plan_versions(plan_id) WHERE decision='APPROVED';
CREATE TRIGGER pm_version_guard BEFORE UPDATE OR DELETE ON synqoria_operations.pm_plan_versions
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.m23_versioned_row_guard();

-- 4. Occurrences: deterministic id => regenerating the same schedule can never double-schedule.
CREATE TABLE synqoria_operations.pm_occurrences(
 occurrence_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 plan_id uuid NOT NULL,
 plan_version integer NOT NULL,
 seq integer NOT NULL CHECK(seq>=0),
 asset_ref uuid NOT NULL,
 kind varchar(28) NOT NULL CHECK(kind IN('PREVENTIVE','CALIBRATION','PREVENTIVE_AND_CALIBRATION')),
 due_at timestamptz NOT NULL,
 state varchar(12) NOT NULL DEFAULT 'PLANNED' CHECK(state IN('PLANNED','WORK_LINKED','COMPLETED','CANCELLED')),
 work_order_ref uuid,
 linked_by uuid,
 linked_at timestamptz,
 completed_by uuid,
 completed_at timestamptz,
 generated_fence bigint NOT NULL CHECK(generated_fence>=1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,plan_id,plan_version,seq),
 FOREIGN KEY(plan_id,plan_version) REFERENCES synqoria_operations.pm_plan_versions(plan_id,version),
 FOREIGN KEY(cell_id,facility_id,asset_ref) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,work_order_ref) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 CONSTRAINT pm_occ_link_consistent CHECK((state IN('WORK_LINKED','COMPLETED'))=(work_order_ref IS NOT NULL)),
 CONSTRAINT pm_occ_completion_consistent CHECK((state='COMPLETED')=(completed_by IS NOT NULL AND completed_at IS NOT NULL))
);
CREATE INDEX pm_occ_calendar ON synqoria_operations.pm_occurrences(cell_id,facility_id,due_at,occurrence_id);
CREATE INDEX pm_occ_asset ON synqoria_operations.pm_occurrences(cell_id,facility_id,asset_ref,due_at DESC);
-- An asset can be under at most one linked servicing occurrence, and a work order serves at most one occurrence.
CREATE UNIQUE INDEX pm_one_active_service_per_asset ON synqoria_operations.pm_occurrences(cell_id,facility_id,asset_ref) WHERE state='WORK_LINKED';
CREATE UNIQUE INDEX pm_one_occurrence_per_work_order ON synqoria_operations.pm_occurrences(cell_id,facility_id,work_order_ref) WHERE work_order_ref IS NOT NULL;

CREATE FUNCTION synqoria_operations.m23_occurrence_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE asset_status text; wo_asset text; wo_state text; cal_ok boolean;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm23_occurrence_history_immutable' USING ERRCODE='23000'; END IF;
 IF (to_jsonb(NEW)-'state'-'work_order_ref'-'linked_by'-'linked_at'-'completed_by'-'completed_at')
    IS DISTINCT FROM (to_jsonb(OLD)-'state'-'work_order_ref'-'linked_by'-'linked_at'-'completed_by'-'completed_at') THEN
  RAISE EXCEPTION 'm23_occurrence_content_immutable' USING ERRCODE='23000';
 END IF;
 IF NEW.state=OLD.state THEN
  IF to_jsonb(NEW) IS DISTINCT FROM to_jsonb(OLD) THEN RAISE EXCEPTION 'm23_occurrence_content_immutable' USING ERRCODE='23000'; END IF;
  RETURN NEW;
 END IF;
 IF OLD.state='PLANNED' AND NEW.state='CANCELLED' THEN RETURN NEW; END IF;
 IF OLD.state='PLANNED' AND NEW.state='WORK_LINKED' THEN
  SELECT status INTO asset_status FROM synqoria_operations.items
   WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND id=NEW.asset_ref AND module_id='M23' AND record_type='ASSET';
  IF asset_status IS DISTINCT FROM 'MAINTENANCE' THEN RAISE EXCEPTION 'm23_asset_not_offline_for_service' USING ERRCODE='23000'; END IF;
  SELECT fields->>'assetRef' INTO wo_asset FROM synqoria_operations.items
   WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND id=NEW.work_order_ref AND module_id='M23' AND record_type='WORK_ORDER';
  IF wo_asset IS DISTINCT FROM NEW.asset_ref::text THEN RAISE EXCEPTION 'm23_work_order_asset_mismatch' USING ERRCODE='23000'; END IF;
  RETURN NEW;
 END IF;
 IF OLD.state='WORK_LINKED' AND NEW.state='COMPLETED' THEN
  SELECT state INTO wo_state FROM synqoria_operations.maintenance_orders
   WHERE cell_id=NEW.cell_id AND facility_id=NEW.facility_id AND item_id=NEW.work_order_ref;
  IF wo_state IS DISTINCT FROM 'VERIFIED' THEN RAISE EXCEPTION 'm23_work_order_not_verified' USING ERRCODE='23000'; END IF;
  IF NEW.kind<>'PREVENTIVE' THEN
   SELECT EXISTS(SELECT 1 FROM synqoria_operations.pm_calibrations c
    WHERE c.cell_id=NEW.cell_id AND c.facility_id=NEW.facility_id AND c.asset_ref=NEW.asset_ref
      AND c.performed_at>=OLD.linked_at AND c.next_due_at>clock_timestamp()) INTO cal_ok;
   IF NOT cal_ok THEN RAISE EXCEPTION 'm23_calibration_evidence_required' USING ERRCODE='23000'; END IF;
  END IF;
  RETURN NEW;
 END IF;
 RAISE EXCEPTION 'm23_occurrence_transition_denied' USING ERRCODE='23000';
END $fn$;

-- 5. Calibration records (insert-only, bound to independently issued evidence), service references, cost references.
CREATE TABLE synqoria_operations.pm_calibrations(
 calibration_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 asset_ref uuid NOT NULL,
 performed_at timestamptz NOT NULL,
 next_due_at timestamptz NOT NULL,
 evidence_id uuid NOT NULL UNIQUE REFERENCES synqoria_operations.external_evidence(evidence_id),
 external_ref varchar(160) NOT NULL CHECK(external_ref<>''),
 recorded_by uuid NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,asset_ref) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 CHECK(next_due_at>performed_at)
);
CREATE INDEX pm_calibration_asset ON synqoria_operations.pm_calibrations(cell_id,facility_id,asset_ref,next_due_at DESC);
CREATE FUNCTION synqoria_operations.m23_occurrence_insert_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
 IF NEW.state<>'PLANNED' OR NEW.work_order_ref IS NOT NULL THEN RAISE EXCEPTION 'm23_occurrence_must_start_planned' USING ERRCODE='23000'; END IF;
 RETURN NEW;
END $fn$;
CREATE TRIGGER pm_occurrence_insert_guard BEFORE INSERT ON synqoria_operations.pm_occurrences
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.m23_occurrence_insert_guard();
CREATE TRIGGER pm_occurrence_guard BEFORE UPDATE OR DELETE ON synqoria_operations.pm_occurrences
 FOR EACH ROW EXECUTE FUNCTION synqoria_operations.m23_occurrence_guard();

CREATE TABLE synqoria_operations.pm_service_references(
 reference_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 work_order_ref uuid NOT NULL,
 kind varchar(24) NOT NULL CHECK(kind IN('SUPPLIER_REPORT','ENGINEER_CERTIFICATE','MANUFACTURER_BULLETIN')),
 external_ref varchar(160) NOT NULL CHECK(external_ref<>''),
 issuer_name varchar(160) NOT NULL CHECK(issuer_name<>''),
 recorded_by uuid NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 -- A reference typed by staff is never an attestation; the only trusted proof remains external_evidence.
 attested boolean NOT NULL DEFAULT false CHECK(attested=false),
 FOREIGN KEY(cell_id,facility_id,work_order_ref) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,work_order_ref,kind,external_ref)
);

CREATE TABLE synqoria_operations.pm_cost_references(
 cost_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 work_order_ref uuid NOT NULL,
 amount_minor bigint NOT NULL CHECK(amount_minor>=0 AND amount_minor<=1000000000000),
 currency char(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 cost_centre_ref varchar(160) NOT NULL CHECK(cost_centre_ref<>''),
 note_ref varchar(160),
 recorded_by uuid NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,work_order_ref) REFERENCES synqoria_operations.items(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,work_order_ref,cost_centre_ref,note_ref)
);

-- 6. Escalations (one per occurrence and level) tied to the audit event whose outbox row carries the M37 contract payload.
CREATE TABLE synqoria_operations.pm_escalations(
 escalation_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 occurrence_id uuid NOT NULL REFERENCES synqoria_operations.pm_occurrences(occurrence_id),
 level smallint NOT NULL CHECK(level BETWEEN 1 AND 3),
 raised_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 raised_by uuid NOT NULL,
 event_id uuid NOT NULL,
 UNIQUE(cell_id,facility_id,occurrence_id,level),
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_operations.audit_events(cell_id,facility_id,event_id)
);

-- 7. Planner mutual exclusion + staleness heartbeat (one row per cell/facility), and idempotent command results.
CREATE TABLE synqoria_operations.pm_planner_state(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 holder_id uuid,
 fence bigint NOT NULL DEFAULT 0 CHECK(fence>=0),
 lease_expires_at timestamptz,
 last_started_at timestamptz,
 last_success_at timestamptz,
 last_error_code varchar(64),
 last_generated integer NOT NULL DEFAULT 0 CHECK(last_generated>=0),
 PRIMARY KEY(cell_id,facility_id)
);

CREATE TABLE synqoria_operations.pm_commands(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 result jsonb,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id)
);

-- 8. FORCE RLS + grants (least privilege; history tables are insert-only for the runtime role).
DO $rls$
DECLARE tab text;
BEGIN
 FOREACH tab IN ARRAY ARRAY['pm_policy_versions','pm_plans','pm_plan_versions','pm_occurrences','pm_calibrations',
  'pm_service_references','pm_cost_references','pm_escalations','pm_planner_state','pm_commands']
 LOOP
  EXECUTE format('ALTER TABLE synqoria_operations.%I ENABLE ROW LEVEL SECURITY',tab);
  EXECUTE format('ALTER TABLE synqoria_operations.%I FORCE ROW LEVEL SECURITY',tab);
  EXECUTE format('REVOKE ALL ON synqoria_operations.%I FROM PUBLIC',tab);
  EXECUTE format(
   'CREATE POLICY m23_scoped ON synqoria_operations.%I FOR ALL TO synqoria_operations_runtime '||
   'USING (cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid '||
   'AND facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) '||
   'WITH CHECK (cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid '||
   'AND facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',tab);
 END LOOP;
END $rls$;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.pm_policy_versions TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.pm_plans TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.pm_plan_versions TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.pm_occurrences TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.pm_planner_state TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.pm_calibrations TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.pm_service_references TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.pm_cost_references TO synqoria_operations_runtime;
GRANT SELECT,INSERT ON synqoria_operations.pm_escalations TO synqoria_operations_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_operations.pm_commands TO synqoria_operations_runtime;
COMMIT;
