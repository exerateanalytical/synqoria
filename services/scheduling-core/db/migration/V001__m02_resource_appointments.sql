BEGIN;
CREATE EXTENSION IF NOT EXISTS btree_gist;
DO $guard$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_schedule_runtime'
  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication)
 THEN RAISE EXCEPTION 'm02_restricted_runtime_role_required'; END IF;
END $guard$;
CREATE SCHEMA synqoria_schedule;REVOKE ALL ON SCHEMA synqoria_schedule FROM PUBLIC;
CREATE TABLE synqoria_schedule.patient_proofs(
 proof_id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 patient_ref uuid NOT NULL,issuer varchar(100) NOT NULL,approved_by uuid NOT NULL,
 valid_from timestamptz NOT NULL,valid_until timestamptz NOT NULL,revoked_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,proof_id),CHECK(valid_until>valid_from)
);
CREATE TABLE synqoria_schedule.resources(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 resource_code varchar(64) NOT NULL,resource_type varchar(30) NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','SUSPENDED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_by uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),UNIQUE(cell_id,facility_id,resource_code),
 CHECK(resource_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 CHECK(resource_type IN('CLINIC','ROOM','PROVIDER','EQUIPMENT'))
);
CREATE TABLE synqoria_schedule.slots(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 resource_id uuid NOT NULL,starts_at timestamptz NOT NULL,ends_at timestamptz NOT NULL,
 status varchar(10) NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','BLOCKED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_by uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,resource_id) REFERENCES synqoria_schedule.resources(cell_id,facility_id,id),
 CHECK(ends_at>starts_at),
 EXCLUDE USING gist(cell_id WITH =,facility_id WITH =,resource_id WITH =,
  tstzrange(starts_at,ends_at,'[)') WITH &&)
);
CREATE INDEX m02_slots_available ON synqoria_schedule.slots(cell_id,facility_id,status,starts_at);
CREATE TABLE synqoria_schedule.appointments(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 patient_ref uuid NOT NULL,proof_id uuid NOT NULL,slot_id uuid NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'REQUESTED'
 CHECK(status IN('REQUESTED','CONFIRMED','ARRIVED','FULFILLED','CANCELLED','NO_SHOW')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 creator_id uuid NOT NULL,last_actor uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,slot_id) REFERENCES synqoria_schedule.slots(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,proof_id) REFERENCES synqoria_schedule.patient_proofs(cell_id,facility_id,proof_id)
);
CREATE UNIQUE INDEX m02_one_live_appointment_per_slot
 ON synqoria_schedule.appointments(cell_id,facility_id,slot_id)
 WHERE status IN('REQUESTED','CONFIRMED','ARRIVED');
CREATE INDEX m02_appointment_patient ON synqoria_schedule.appointments
 (cell_id,facility_id,patient_ref,created_at DESC);
CREATE TABLE synqoria_schedule.waitlist(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 patient_ref uuid NOT NULL,proof_id uuid NOT NULL,resource_id uuid NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'WAITING'
 CHECK(status IN('WAITING','OFFERED','BOOKED','CANCELLED')),
 offered_slot_id uuid,appointment_id uuid,
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_by uuid NOT NULL,updated_by uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,resource_id) REFERENCES synqoria_schedule.resources(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,proof_id) REFERENCES synqoria_schedule.patient_proofs(cell_id,facility_id,proof_id),
 FOREIGN KEY(cell_id,facility_id,offered_slot_id) REFERENCES synqoria_schedule.slots(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,appointment_id) REFERENCES synqoria_schedule.appointments(cell_id,facility_id,id),
 CHECK((status IN('OFFERED','BOOKED'))=(offered_slot_id IS NOT NULL)),
 CHECK((status='BOOKED')=(appointment_id IS NOT NULL))
);
CREATE TABLE synqoria_schedule.commands(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,actor_id uuid NOT NULL,
 command_id uuid NOT NULL,request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 response jsonb,http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id),
 CHECK((response IS NULL)=(http_status IS NULL))
);
CREATE TABLE synqoria_schedule.audit_events(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 aggregate_type varchar(20) NOT NULL,aggregate_id uuid NOT NULL,
 action varchar(50) NOT NULL,version bigint NOT NULL,
 actor_id uuid NOT NULL,command_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE synqoria_schedule.outbox(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 aggregate_id uuid NOT NULL,event_type varchar(100) NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(id) REFERENCES synqoria_schedule.audit_events(id)
);
CREATE FUNCTION synqoria_schedule.audit_immutable() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN RAISE EXCEPTION 'm02_immutable_audit' USING ERRCODE='23514';END $f$;
CREATE TRIGGER m02_no_audit_rewrite BEFORE UPDATE OR DELETE ON synqoria_schedule.audit_events
 FOR EACH ROW EXECUTE FUNCTION synqoria_schedule.audit_immutable();
CREATE TRIGGER m02_no_outbox_rewrite BEFORE UPDATE OR DELETE ON synqoria_schedule.outbox
 FOR EACH ROW EXECUTE FUNCTION synqoria_schedule.audit_immutable();
CREATE FUNCTION synqoria_schedule.proof_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm02_proof_delete_denied' USING ERRCODE='23514';END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(OLD.proof_id::text,2));
 IF ROW(NEW.proof_id,NEW.cell_id,NEW.facility_id,NEW.patient_ref,NEW.issuer,
  NEW.approved_by,NEW.valid_from,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.proof_id,OLD.cell_id,OLD.facility_id,OLD.patient_ref,OLD.issuer,
  OLD.approved_by,OLD.valid_from,OLD.created_at)
 OR NEW.valid_until>OLD.valid_until OR (OLD.revoked_at IS NOT NULL AND
 NEW.revoked_at IS DISTINCT FROM OLD.revoked_at)
 THEN RAISE EXCEPTION 'm02_proof_rebinding_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m02_proof_guard BEFORE UPDATE OR DELETE ON synqoria_schedule.patient_proofs
 FOR EACH ROW EXECUTE FUNCTION synqoria_schedule.proof_guard();
CREATE FUNCTION synqoria_schedule.appointment_transition_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm02_appointment_delete_denied' USING ERRCODE='23514';END IF;
 IF NEW.version<>OLD.version+1 OR
 ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.patient_ref,NEW.proof_id,NEW.creator_id,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.patient_ref,OLD.proof_id,OLD.creator_id,OLD.created_at)
 OR NOT (
  (OLD.status='REQUESTED' AND NEW.status IN('CONFIRMED','CANCELLED')) OR
  (OLD.status='CONFIRMED' AND NEW.status IN('ARRIVED','CANCELLED','NO_SHOW')) OR
  (OLD.status='ARRIVED' AND NEW.status IN('FULFILLED','CANCELLED'))
 ) THEN RAISE EXCEPTION 'm02_appointment_transition_denied' USING ERRCODE='23514';END IF;
 IF OLD.status NOT IN('REQUESTED','CONFIRMED') AND NEW.slot_id<>OLD.slot_id THEN
  RAISE EXCEPTION 'm02_slot_change_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m02_appointment_guard BEFORE UPDATE OR DELETE ON synqoria_schedule.appointments
 FOR EACH ROW EXECUTE FUNCTION synqoria_schedule.appointment_transition_guard();
DO $policy$ DECLARE rel text;BEGIN
 FOREACH rel IN ARRAY ARRAY['patient_proofs','resources','slots','appointments','waitlist','commands','audit_events','outbox']
 LOOP
  EXECUTE format('ALTER TABLE synqoria_schedule.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_schedule.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('CREATE POLICY hospital_scope ON synqoria_schedule.%I USING
  (cell_id::text=current_setting(''synqoria.cell'',true)
   AND facility_id::text=current_setting(''synqoria.facility'',true))
  WITH CHECK(cell_id::text=current_setting(''synqoria.cell'',true)
   AND facility_id::text=current_setting(''synqoria.facility'',true))',rel);
 END LOOP;
END $policy$;
GRANT USAGE ON SCHEMA synqoria_schedule TO synqoria_schedule_runtime;
GRANT SELECT ON ALL TABLES IN SCHEMA synqoria_schedule TO synqoria_schedule_runtime;
GRANT INSERT,UPDATE ON synqoria_schedule.resources,synqoria_schedule.slots,
 synqoria_schedule.appointments,synqoria_schedule.waitlist,synqoria_schedule.commands
 TO synqoria_schedule_runtime;
GRANT INSERT ON synqoria_schedule.audit_events,synqoria_schedule.outbox TO synqoria_schedule_runtime;
REVOKE ALL ON FUNCTION synqoria_schedule.audit_immutable() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_schedule.proof_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_schedule.appointment_transition_guard() FROM PUBLIC;
COMMIT;