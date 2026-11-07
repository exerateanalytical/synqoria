-- SYNQORIA M26 Catering and dietary orders (owner: Nutrition operations / service).
-- Apply with a separate DATABASE MIGRATION role, never the runtime login.
-- Provision synqoria_nutrition_runtime (LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEROLE
-- NOCREATEDB NOREPLICATION) through the approved vault/DBA process before applying.
--
-- Authority boundaries (architecture v2.1):
--  * patient_context, allergy_snapshots and fasting_instructions are READ PROJECTIONS of verified
--    upstream contracts (identity, allergy and clinical fasting authorities). They are keyed by the
--    upstream opaque reference and carry the upstream source version; M26 never edits their meaning,
--    never issues patient identifiers and is not a master patient index or allergy authority.
--  * dietary_orders, meal_services, production_batches, substitutions, trays and
--    delivery_incidents are the M26-owned operational records.
BEGIN;

DO $guard$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_nutrition_runtime'
  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole
  AND NOT rolcreatedb AND NOT rolreplication AND NOT rolinherit)
 THEN RAISE EXCEPTION 'missing independent restricted synqoria_nutrition_runtime';
 END IF;
END $guard$;

CREATE SCHEMA IF NOT EXISTS synqoria_nutrition;
REVOKE ALL ON SCHEMA synqoria_nutrition FROM PUBLIC;

-- Controlled-vocabulary codes: upper-case token, 2..40 chars, bounded list size.
CREATE FUNCTION synqoria_nutrition.valid_codes(codes text[], max_items int)
RETURNS boolean LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $f$
 SELECT codes IS NOT NULL AND cardinality(codes) <= max_items
  AND NOT EXISTS(SELECT 1 FROM unnest(codes) c WHERE c IS NULL OR c !~ '^[A-Z0-9_]{2,40}$')
$f$;

-- ---------------------------------------------------------------- consumed upstream projections
CREATE TABLE synqoria_nutrition.patient_context(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 patient_ref uuid NOT NULL,
 patient_label varchar(120) NOT NULL CHECK(length(btrim(patient_label)) >= 1),
 ward_code varchar(32) NOT NULL CHECK(ward_code ~ '^[A-Z0-9_-]{1,32}$'),
 bed_code varchar(32) NOT NULL CHECK(bed_code ~ '^[A-Z0-9_-]{1,32}$'),
 admitted boolean NOT NULL,
 identity_version bigint NOT NULL CHECK(identity_version >= 1),
 verified_at timestamptz NOT NULL,
 source_event_id uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,patient_ref)
);
CREATE INDEX patient_context_ward ON synqoria_nutrition.patient_context
 (cell_id,facility_id,ward_code,bed_code);

CREATE TABLE synqoria_nutrition.allergy_snapshots(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 patient_ref uuid NOT NULL,
 allergen_codes text[] NOT NULL CHECK(synqoria_nutrition.valid_codes(allergen_codes,64)),
 no_known_allergies boolean NOT NULL,
 source_version bigint NOT NULL CHECK(source_version >= 1),
 verified_at timestamptz NOT NULL,
 source_event_id uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,patient_ref),
 CONSTRAINT nka_matches_list CHECK(no_known_allergies = (cardinality(allergen_codes) = 0))
);

CREATE TABLE synqoria_nutrition.fasting_instructions(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 instruction_ref uuid NOT NULL,
 patient_ref uuid NOT NULL,
 status varchar(12) NOT NULL CHECK(status IN ('ACTIVE','REVOKED','COMPLETED')),
 reason_code varchar(40) NOT NULL CHECK(reason_code ~ '^[A-Z0-9_]{2,40}$'),
 starts_at timestamptz NOT NULL,
 ends_at timestamptz,
 source_version bigint NOT NULL CHECK(source_version >= 1),
 verified_at timestamptz NOT NULL,
 source_event_id uuid NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,instruction_ref),
 CHECK(ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX fasting_patient ON synqoria_nutrition.fasting_instructions
 (cell_id,facility_id,patient_ref,status);

CREATE TABLE synqoria_nutrition.consumed_events(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 source_event_id uuid NOT NULL,
 kind varchar(24) NOT NULL CHECK(kind IN ('PATIENT_CONTEXT','ALLERGY','FASTING')),
 consumed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,source_event_id)
);

-- ---------------------------------------------------------------- dietary orders (S02)
CREATE TABLE synqoria_nutrition.dietary_orders(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 patient_ref uuid NOT NULL,
 clinical_order_ref uuid NOT NULL,
 status varchar(12) NOT NULL CHECK(status IN
  ('PRESCRIBED','APPROVED','SUPERSEDED','CANCELLED','REVOKED','EXPIRED')),
 diet_category varchar(20) NOT NULL CHECK(diet_category IN
  ('REGULAR','DIABETIC','RENAL','CARDIAC','LOW_SODIUM','HIGH_PROTEIN',
   'CLEAR_LIQUID','FULL_LIQUID','PUREED','NIL_BY_MOUTH')),
 texture varchar(20) NOT NULL CHECK(texture IN
  ('NORMAL','SOFT_BITE_SIZED','MINCED_MOIST','PUREED','LIQUIDISED','NONE')),
 restrictions text[] NOT NULL CHECK(synqoria_nutrition.valid_codes(restrictions,20)),
 excluded_allergens text[] NOT NULL CHECK(synqoria_nutrition.valid_codes(excluded_allergens,64)),
 meal_timing text[] NOT NULL CHECK(meal_timing <@ ARRAY['BREAKFAST','LUNCH','DINNER','SNACK']::text[]),
 special_instructions varchar(500) NOT NULL DEFAULT '',
 effective_from timestamptz NOT NULL,
 effective_to timestamptz,
 prescriber_id uuid NOT NULL,
 approver_id uuid,
 approved_at timestamptz,
 reconciled_allergy_version bigint,
 reconciled_at timestamptz,
 supersedes_id uuid,
 status_reason varchar(300),
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,patient_ref)
  REFERENCES synqoria_nutrition.patient_context(cell_id,facility_id,patient_ref),
 FOREIGN KEY(cell_id,facility_id,supersedes_id)
  REFERENCES synqoria_nutrition.dietary_orders(cell_id,facility_id,id),
 CHECK(effective_to IS NULL OR effective_to > effective_from),
 -- independent approval: the checker is never the prescriber
 CONSTRAINT approver_independent CHECK(approver_id IS NULL OR approver_id <> prescriber_id),
 CONSTRAINT approved_has_evidence CHECK(status NOT IN ('APPROVED','SUPERSEDED')
  OR (approver_id IS NOT NULL AND approved_at IS NOT NULL AND reconciled_allergy_version IS NOT NULL)),
 -- explicit clinical-content invariants (diet / texture / meal-timing contradictions)
 CONSTRAINT diet_texture_consistent CHECK(
  (diet_category = 'NIL_BY_MOUTH') = (texture = 'NONE')
  AND (diet_category IN ('CLEAR_LIQUID','FULL_LIQUID') = (texture = 'LIQUIDISED')
       OR diet_category = 'NIL_BY_MOUTH')
  AND (diet_category <> 'PUREED' OR texture = 'PUREED')),
 CONSTRAINT nbm_no_meals CHECK(diet_category <> 'NIL_BY_MOUTH' OR cardinality(meal_timing) = 0),
 CONSTRAINT meals_required CHECK(diet_category = 'NIL_BY_MOUTH' OR cardinality(meal_timing) >= 1)
);
-- exactly one current approved order per patient; amendment supersedes atomically
CREATE UNIQUE INDEX dietary_orders_one_approved ON synqoria_nutrition.dietary_orders
 (cell_id,facility_id,patient_ref) WHERE status = 'APPROVED';
-- at most one pending (not yet approved) order per patient
CREATE UNIQUE INDEX dietary_orders_one_pending ON synqoria_nutrition.dietary_orders
 (cell_id,facility_id,patient_ref) WHERE status = 'PRESCRIBED';
CREATE INDEX dietary_orders_patient ON synqoria_nutrition.dietary_orders
 (cell_id,facility_id,patient_ref,created_at DESC);
CREATE INDEX dietary_orders_status ON synqoria_nutrition.dietary_orders
 (cell_id,facility_id,status,updated_at DESC,id);

-- ---------------------------------------------------------------- production planning (S03)
CREATE TABLE synqoria_nutrition.production_batches(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 ward_code varchar(32) NOT NULL CHECK(ward_code ~ '^[A-Z0-9_-]{1,32}$'),
 meal_date date NOT NULL,
 meal_type varchar(9) NOT NULL CHECK(meal_type IN ('BREAKFAST','LUNCH','DINNER','SNACK')),
 status varchar(20) NOT NULL CHECK(status IN
  ('DRAFT','RELEASED','PREPARED','DISPATCH_APPROVED','CANCELLED')),
 cutoff_at timestamptz NOT NULL,
 planned_by uuid NOT NULL,
 released_by uuid,
 prepared_by uuid,
 dispatch_approved_by uuid,
 dispatch_approved_at timestamptz,
 portions jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(portions)='object'),
 exceptions jsonb NOT NULL DEFAULT '[]'::jsonb CHECK(jsonb_typeof(exceptions)='array'),
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 CONSTRAINT dispatch_independent CHECK(dispatch_approved_by IS NULL
  OR (dispatch_approved_by <> planned_by AND dispatch_approved_by IS DISTINCT FROM prepared_by)),
 CONSTRAINT dispatch_has_evidence CHECK((status = 'DISPATCH_APPROVED') =
  (dispatch_approved_by IS NOT NULL AND dispatch_approved_at IS NOT NULL))
);
CREATE UNIQUE INDEX batches_one_live ON synqoria_nutrition.production_batches
 (cell_id,facility_id,ward_code,meal_date,meal_type) WHERE status <> 'CANCELLED';
CREATE INDEX batches_list ON synqoria_nutrition.production_batches
 (cell_id,facility_id,meal_date DESC,ward_code,id);

CREATE TABLE synqoria_nutrition.meal_services(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 batch_id uuid NOT NULL,
 patient_ref uuid NOT NULL,
 order_id uuid NOT NULL,
 ward_code varchar(32) NOT NULL,
 bed_code varchar(32) NOT NULL,
 meal_date date NOT NULL,
 meal_type varchar(9) NOT NULL CHECK(meal_type IN ('BREAKFAST','LUNCH','DINNER','SNACK')),
 diet_category varchar(20) NOT NULL,
 texture varchar(20) NOT NULL,
 status varchar(14) NOT NULL CHECK(status IN
  ('PLANNED','PREPARED','DISPATCHED','DELIVERED','ACKNOWLEDGED','HELD','CANCELLED')),
 hold_reason varchar(60),
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,batch_id)
  REFERENCES synqoria_nutrition.production_batches(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,order_id)
  REFERENCES synqoria_nutrition.dietary_orders(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,patient_ref)
  REFERENCES synqoria_nutrition.patient_context(cell_id,facility_id,patient_ref),
 CONSTRAINT hold_has_reason CHECK((status = 'HELD') = (hold_reason IS NOT NULL))
);
-- duplicate meal issuance is structurally impossible: one live meal per patient/date/meal type
CREATE UNIQUE INDEX meal_one_live ON synqoria_nutrition.meal_services
 (cell_id,facility_id,patient_ref,meal_date,meal_type) WHERE status <> 'CANCELLED';
CREATE INDEX meal_batch ON synqoria_nutrition.meal_services (cell_id,facility_id,batch_id);
CREATE INDEX meal_order ON synqoria_nutrition.meal_services (cell_id,facility_id,order_id);

CREATE TABLE synqoria_nutrition.substitutions(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 meal_service_id uuid NOT NULL,
 original_item_code varchar(40) NOT NULL CHECK(original_item_code ~ '^[A-Z0-9_]{2,40}$'),
 substitute_item_code varchar(40) NOT NULL CHECK(substitute_item_code ~ '^[A-Z0-9_]{2,40}$'),
 substitute_allergens text[] NOT NULL CHECK(synqoria_nutrition.valid_codes(substitute_allergens,64)),
 reason_code varchar(40) NOT NULL CHECK(reason_code ~ '^[A-Z0-9_]{2,40}$'),
 status varchar(10) NOT NULL CHECK(status IN ('PROPOSED','APPROVED','REJECTED')),
 proposed_by uuid NOT NULL,
 decided_by uuid,
 decided_at timestamptz,
 decision_note varchar(300),
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,meal_service_id)
  REFERENCES synqoria_nutrition.meal_services(cell_id,facility_id,id),
 CHECK(original_item_code <> substitute_item_code),
 CONSTRAINT subst_decider_independent CHECK(decided_by IS NULL OR decided_by <> proposed_by),
 CONSTRAINT subst_decision_atomic CHECK((status = 'PROPOSED') = (decided_by IS NULL))
);
CREATE INDEX subst_meal ON synqoria_nutrition.substitutions (cell_id,facility_id,meal_service_id);

-- ---------------------------------------------------------------- delivery (S04)
CREATE TABLE synqoria_nutrition.trays(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 meal_service_id uuid NOT NULL,
 tray_code varchar(40) NOT NULL CHECK(tray_code ~ '^[A-Z0-9_-]{3,40}$'),
 status varchar(13) NOT NULL CHECK(status IN
  ('LOADED','IN_TRANSIT','DELIVERED','ACKNOWLEDGED','EXCEPTION','RETURNED')),
 loaded_by uuid NOT NULL,
 dispatched_by uuid,
 dispatched_at timestamptz,
 delivered_by uuid,
 delivered_at timestamptz,
 identity_method varchar(16) CHECK(identity_method IN ('WRISTBAND_SCAN','DUAL_VERBAL')),
 identity_confirmed_ref uuid,
 acknowledged_by uuid,
 acknowledged_at timestamptz,
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 -- one tray per meal service: a second tray for the same meal cannot be issued
 UNIQUE(cell_id,facility_id,meal_service_id),
 UNIQUE(cell_id,facility_id,tray_code),
 FOREIGN KEY(cell_id,facility_id,meal_service_id)
  REFERENCES synqoria_nutrition.meal_services(cell_id,facility_id,id),
 CONSTRAINT delivered_has_identity CHECK(status NOT IN ('DELIVERED','ACKNOWLEDGED')
  OR (delivered_by IS NOT NULL AND delivered_at IS NOT NULL
      AND identity_method IS NOT NULL AND identity_confirmed_ref IS NOT NULL)),
 CONSTRAINT ack_has_evidence CHECK((status = 'ACKNOWLEDGED') =
  (acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL)),
 CONSTRAINT ack_independent CHECK(acknowledged_by IS NULL OR acknowledged_by <> delivered_by)
);
CREATE INDEX trays_status ON synqoria_nutrition.trays (cell_id,facility_id,status,updated_at DESC,id);

CREATE TABLE synqoria_nutrition.delivery_incidents(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 meal_service_id uuid NOT NULL,
 tray_id uuid,
 incident_type varchar(20) NOT NULL CHECK(incident_type IN
  ('WRONG_PATIENT','ALLERGEN_RISK','DIET_MISMATCH','DIET_UNKNOWN','REFUSED','LATE','MISSING_TRAY','OTHER')),
 severity varchar(8) NOT NULL CHECK(severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
 status varchar(10) NOT NULL CHECK(status IN ('OPEN','ESCALATED','RESOLVED')),
 description varchar(500) NOT NULL DEFAULT '',
 reporter_id uuid NOT NULL,
 escalated_to_role varchar(40),
 escalated_by uuid,
 escalated_at timestamptz,
 resolved_by uuid,
 resolved_at timestamptz,
 resolution_note varchar(500),
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,meal_service_id)
  REFERENCES synqoria_nutrition.meal_services(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,tray_id)
  REFERENCES synqoria_nutrition.trays(cell_id,facility_id,id),
 CONSTRAINT escalation_atomic CHECK((status = 'OPEN') OR escalated_at IS NOT NULL),
 CONSTRAINT resolved_atomic CHECK((status = 'RESOLVED') = (resolved_at IS NOT NULL AND resolved_by IS NOT NULL))
);
CREATE INDEX incidents_status ON synqoria_nutrition.delivery_incidents
 (cell_id,facility_id,status,created_at DESC,id);

-- ---------------------------------------------------------------- idempotency, audit, outbox
CREATE TABLE synqoria_nutrition.command_idempotency(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 response jsonb,
 http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id),
 CHECK((response IS NULL) = (http_status IS NULL))
);

CREATE TABLE synqoria_nutrition.audit_events(
 event_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 aggregate_type varchar(24) NOT NULL,
 aggregate_id uuid NOT NULL,
 action varchar(48) NOT NULL,
 from_status varchar(20),
 to_status varchar(20) NOT NULL,
 aggregate_version bigint NOT NULL CHECK(aggregate_version >= 1),
 actor_id uuid NOT NULL,
 command_id uuid NOT NULL,
 details jsonb NOT NULL CHECK(jsonb_typeof(details)='object'),
 digest char(64) NOT NULL CHECK(digest ~ '^[0-9a-f]{64}$'),
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id)
);
CREATE INDEX audit_aggregate ON synqoria_nutrition.audit_events
 (cell_id,facility_id,aggregate_type,aggregate_id,occurred_at,event_id);

CREATE TABLE synqoria_nutrition.outbox(
 event_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 event_type varchar(64) NOT NULL,
 aggregate_id uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id)
  REFERENCES synqoria_nutrition.audit_events(cell_id,facility_id,event_id)
);
CREATE INDEX outbox_unpublished ON synqoria_nutrition.outbox (created_at,event_id)
 WHERE published_at IS NULL;

-- ---------------------------------------------------------------- immutability
CREATE FUNCTION synqoria_nutrition.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 RAISE EXCEPTION 'm26_immutable_record: % on %.% is not permitted', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME
  USING ERRCODE = 'integrity_constraint_violation';
END $t$;
CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON synqoria_nutrition.audit_events
 FOR EACH ROW EXECUTE FUNCTION synqoria_nutrition.forbid_mutation();
CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE ON synqoria_nutrition.audit_events
 FOR EACH STATEMENT EXECUTE FUNCTION synqoria_nutrition.forbid_mutation();

-- Approved clinical content is immutable: once a dietary order leaves PRESCRIBED only status
-- bookkeeping may change. An amendment is a NEW order that supersedes the approved one.
CREATE FUNCTION synqoria_nutrition.protect_order_content() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'm26_immutable_record: dietary orders are never deleted'
   USING ERRCODE = 'integrity_constraint_violation';
 END IF;
 IF OLD.status <> 'PRESCRIBED' AND (
   NEW.patient_ref IS DISTINCT FROM OLD.patient_ref OR NEW.clinical_order_ref IS DISTINCT FROM OLD.clinical_order_ref
   OR NEW.diet_category IS DISTINCT FROM OLD.diet_category OR NEW.texture IS DISTINCT FROM OLD.texture
   OR NEW.restrictions IS DISTINCT FROM OLD.restrictions OR NEW.excluded_allergens IS DISTINCT FROM OLD.excluded_allergens
   OR NEW.meal_timing IS DISTINCT FROM OLD.meal_timing OR NEW.special_instructions IS DISTINCT FROM OLD.special_instructions
   OR NEW.effective_from IS DISTINCT FROM OLD.effective_from OR NEW.effective_to IS DISTINCT FROM OLD.effective_to
   OR NEW.prescriber_id IS DISTINCT FROM OLD.prescriber_id OR NEW.supersedes_id IS DISTINCT FROM OLD.supersedes_id
   OR (OLD.approver_id IS NOT NULL AND (NEW.approver_id IS DISTINCT FROM OLD.approver_id
       OR NEW.approved_at IS DISTINCT FROM OLD.approved_at
       OR NEW.reconciled_allergy_version IS DISTINCT FROM OLD.reconciled_allergy_version)))
 THEN
  RAISE EXCEPTION 'm26_immutable_record: approved dietary order content cannot change'
   USING ERRCODE = 'integrity_constraint_violation';
 END IF;
 IF OLD.status IN ('SUPERSEDED','CANCELLED','REVOKED','EXPIRED') AND NEW.status <> OLD.status THEN
  RAISE EXCEPTION 'm26_immutable_record: terminal dietary order status cannot change'
   USING ERRCODE = 'integrity_constraint_violation';
 END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER dietary_orders_protect BEFORE UPDATE OR DELETE ON synqoria_nutrition.dietary_orders
 FOR EACH ROW EXECUTE FUNCTION synqoria_nutrition.protect_order_content();

CREATE FUNCTION synqoria_nutrition.protect_delivery_evidence() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF OLD.delivered_at IS NOT NULL AND (NEW.delivered_at IS DISTINCT FROM OLD.delivered_at
    OR NEW.delivered_by IS DISTINCT FROM OLD.delivered_by
    OR NEW.identity_confirmed_ref IS DISTINCT FROM OLD.identity_confirmed_ref
    OR NEW.identity_method IS DISTINCT FROM OLD.identity_method)
    OR OLD.acknowledged_at IS NOT NULL AND (NEW.acknowledged_at IS DISTINCT FROM OLD.acknowledged_at
    OR NEW.acknowledged_by IS DISTINCT FROM OLD.acknowledged_by)
 THEN
  RAISE EXCEPTION 'm26_immutable_record: recorded delivery evidence cannot change'
   USING ERRCODE = 'integrity_constraint_violation';
 END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER trays_protect BEFORE UPDATE ON synqoria_nutrition.trays
 FOR EACH ROW EXECUTE FUNCTION synqoria_nutrition.protect_delivery_evidence();

-- ---------------------------------------------------------------- grants and forced RLS
GRANT USAGE ON SCHEMA synqoria_nutrition TO synqoria_nutrition_runtime;
GRANT EXECUTE ON FUNCTION synqoria_nutrition.valid_codes(text[],int) TO synqoria_nutrition_runtime;
-- mutable operational tables: no DELETE for the runtime principal anywhere
GRANT SELECT,INSERT,UPDATE ON
 synqoria_nutrition.patient_context, synqoria_nutrition.allergy_snapshots,
 synqoria_nutrition.fasting_instructions, synqoria_nutrition.dietary_orders,
 synqoria_nutrition.production_batches, synqoria_nutrition.meal_services,
 synqoria_nutrition.substitutions, synqoria_nutrition.trays,
 synqoria_nutrition.delivery_incidents, synqoria_nutrition.command_idempotency
 TO synqoria_nutrition_runtime;
GRANT SELECT,INSERT ON synqoria_nutrition.consumed_events, synqoria_nutrition.audit_events,
 synqoria_nutrition.outbox TO synqoria_nutrition_runtime;
-- the independent outbox relay role (provisioned separately) owns published_at updates.

DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['patient_context','allergy_snapshots','fasting_instructions',
  'consumed_events','dietary_orders','production_batches','meal_services','substitutions',
  'trays','delivery_incidents','command_idempotency','audit_events','outbox']
 LOOP
  EXECUTE format('ALTER TABLE synqoria_nutrition.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_nutrition.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format(
   'CREATE POLICY nutrition_scoped_access ON synqoria_nutrition.%I ' ||
   'FOR ALL TO synqoria_nutrition_runtime ' ||
   'USING (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) ' ||
   'WITH CHECK (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;
COMMIT;
