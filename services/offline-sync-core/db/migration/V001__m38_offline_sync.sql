-- SYNQORIA M38 Offline synchronization and ownership reconciliation.
-- Apply with a separate DATABASE MIGRATION role, never the runtime login.
-- Provision synqoria_offline_runtime (LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEROLE NOCREATEDB NOREPLICATION) first.
-- Boundary: this service owns the SYNC ENVELOPE (devices, queued command evidence, conflicts, ownership epochs and reconciliation decisions).
-- It does NOT own clinical data, identity or authorization. Record "heads" are version/hash/epoch registrations published by the owning
-- domain; an accepted command is dispatched to that domain through the outbox. Nothing here overwrites a clinical record silently.
BEGIN;
DO $guard$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_offline_runtime' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication AND NOT rolinherit)
 THEN RAISE EXCEPTION 'missing independent restricted synqoria_offline_runtime'; END IF;
END $guard$;
CREATE SCHEMA IF NOT EXISTS synqoria_offline;
REVOKE ALL ON SCHEMA synqoria_offline FROM PUBLIC;

-- ---------------------------------------------------------------- B01: which operations may legally function offline
CREATE TABLE synqoria_offline.contracts(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 operation_type varchar(64) NOT NULL CHECK(operation_type ~ '^[a-z][a-z0-9_.-]{2,63}$'),
 record_type varchar(48) NOT NULL CHECK(record_type ~ '^[a-z][a-z0-9_.-]{1,47}$'),
 owner_domain varchar(64) NOT NULL CHECK(length(btrim(owner_domain))>0),
 offline_allowed boolean NOT NULL,
 requires_safety_gate boolean NOT NULL,                       -- a required clinical gate can never be satisfied offline
 mergeable boolean NOT NULL,                                  -- only disjoint-field changes may auto-merge; clinical default is false
 max_queue_age_minutes int NOT NULL CHECK(max_queue_age_minutes BETWEEN 1 AND 20160),
 status varchar(10) NOT NULL CHECK(status IN ('DRAFT','APPROVED','RETIRED','REJECTED')),
 created_by uuid NOT NULL, approved_by uuid, version bigint NOT NULL CHECK(version>=1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,operation_type),
 CONSTRAINT contract_checker_independent CHECK(approved_by IS NULL OR approved_by<>created_by),
 CONSTRAINT gate_means_online_only CHECK(NOT(requires_safety_gate AND offline_allowed)),
 CONSTRAINT approved_has_approver CHECK(status<>'APPROVED' OR approved_by IS NOT NULL));

-- ---------------------------------------------------------------- B04: authoritative heads, ownership epochs
CREATE TABLE synqoria_offline.record_heads(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 record_type varchar(48) NOT NULL CHECK(record_type ~ '^[a-z][a-z0-9_.-]{1,47}$'),
 record_key varchar(120) NOT NULL CHECK(length(record_key) BETWEEN 1 AND 120),
 owner_ref uuid NOT NULL,                                     -- the authoritative cell for this record
 ownership_epoch bigint NOT NULL CHECK(ownership_epoch>=1),
 head_version bigint NOT NULL CHECK(head_version>=1), head_hash char(64) NOT NULL CHECK(head_hash ~ '^[0-9a-f]{64}$'),
 frozen boolean NOT NULL DEFAULT false, version bigint NOT NULL CHECK(version>=1),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,record_type,record_key));
CREATE TABLE synqoria_offline.record_revisions(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, head_id uuid NOT NULL,
 head_version bigint NOT NULL CHECK(head_version>=1), head_hash char(64) NOT NULL CHECK(head_hash ~ '^[0-9a-f]{64}$'),
 changed_fields text[] NOT NULL CHECK(cardinality(changed_fields)<=40), source varchar(16) NOT NULL CHECK(source IN ('DOMAIN','OFFLINE','RESOLUTION')),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,head_id,head_version),
 FOREIGN KEY(cell_id,facility_id,head_id) REFERENCES synqoria_offline.record_heads(cell_id,facility_id,id));

-- ---------------------------------------------------------------- B02/B08: devices
CREATE TABLE synqoria_offline.devices(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 device_key varchar(64) NOT NULL CHECK(device_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{5,63}$'),
 label varchar(120) NOT NULL CHECK(length(btrim(label))>0),
 bound_user uuid NOT NULL,                                    -- the identity this device acts for; P03 remains the identity authority
 status varchar(8) NOT NULL CHECK(status IN ('ACTIVE','REVOKED')),
 session_expires_at timestamptz NOT NULL,
 queue_limit int NOT NULL CHECK(queue_limit BETWEEN 1 AND 5000),
 last_seq bigint NOT NULL DEFAULT 0 CHECK(last_seq>=0),       -- resumable cursor: highest acknowledged device sequence
 last_seen_at timestamptz, registered_by uuid NOT NULL, revoked_by uuid, revoke_reason varchar(300),
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,device_key),
 CONSTRAINT revoked_has_reason CHECK(status<>'REVOKED' OR (revoked_by IS NOT NULL AND length(btrim(revoke_reason))>0)));

-- ---------------------------------------------------------------- B02/B03: immutable command envelopes
CREATE TABLE synqoria_offline.commands(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, device_id uuid NOT NULL,
 command_id uuid NOT NULL,                                    -- client idempotency key, immutable identifier
 device_seq bigint NOT NULL CHECK(device_seq>=1),
 operation_type varchar(64) NOT NULL, record_type varchar(48) NOT NULL, record_key varchar(120) NOT NULL,
 base_version bigint NOT NULL CHECK(base_version>=1), base_epoch bigint NOT NULL CHECK(base_epoch>=1),
 fields text[] NOT NULL CHECK(cardinality(fields)<=40),
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object' AND octet_length(payload::text)<=16384),
 payload_hash char(64) NOT NULL CHECK(payload_hash ~ '^[0-9a-f]{64}$'),
 created_local_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 author_id uuid NOT NULL,
 status varchar(10) NOT NULL CHECK(status IN ('APPLIED','CONFLICT','REJECTED','EXPIRED','RESOLVED')),
 outcome_code varchar(48) NOT NULL, applied_version bigint, version bigint NOT NULL CHECK(version>=1),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,device_id,command_id), UNIQUE(cell_id,facility_id,device_id,device_seq),
 FOREIGN KEY(cell_id,facility_id,device_id) REFERENCES synqoria_offline.devices(cell_id,facility_id,id),
 CONSTRAINT expiry_after_creation CHECK(expires_at>created_local_at),
 CONSTRAINT applied_has_version CHECK((status='APPLIED')=(applied_version IS NOT NULL) OR status='RESOLVED'));
CREATE INDEX commands_queue ON synqoria_offline.commands (cell_id,facility_id,status,received_at DESC);

-- ---------------------------------------------------------------- B05/B06: conflicts and immutable decisions
CREATE TABLE synqoria_offline.conflicts(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, command_row uuid NOT NULL, head_id uuid NOT NULL,
 kind varchar(16) NOT NULL CHECK(kind IN ('STALE_BASE','INCOMPATIBLE','OWNER_FENCED')),
 local_base_version bigint NOT NULL, local_payload_hash char(64) NOT NULL,
 authoritative_version bigint NOT NULL, authoritative_hash char(64) NOT NULL, authoritative_epoch bigint NOT NULL,
 differing_fields text[] NOT NULL,
 status varchar(8) NOT NULL CHECK(status IN ('OPEN','RESOLVED')),
 resolution varchar(20) CHECK(resolution IN ('ACCEPT_LOCAL','KEEP_AUTHORITATIVE')),
 reason_code varchar(24) CHECK(reason_code IN ('CLINICAL_REVIEW','DUPLICATE_ENTRY','SUPERSEDED_BY_NEWER','DATA_ERROR','OTHER')),
 reason_note varchar(500), resolved_by uuid, resolved_at timestamptz, resulting_version bigint,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,command_row),
 FOREIGN KEY(cell_id,facility_id,command_row) REFERENCES synqoria_offline.commands(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,head_id) REFERENCES synqoria_offline.record_heads(cell_id,facility_id,id),
 CONSTRAINT resolved_is_complete CHECK((status='RESOLVED')=(resolution IS NOT NULL AND reason_code IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)),
 CONSTRAINT other_needs_note CHECK(reason_code IS DISTINCT FROM 'OTHER' OR length(btrim(coalesce(reason_note,'')))>=5));

-- ---------------------------------------------------------------- B04: ownership transfers (maker-checker, positive fencing evidence)
CREATE TABLE synqoria_offline.ownership_transfers(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, head_id uuid NOT NULL,
 from_owner uuid NOT NULL, to_owner uuid NOT NULL CHECK(to_owner<>from_owner), from_epoch bigint NOT NULL,
 status varchar(10) NOT NULL CHECK(status IN ('PROPOSED','FROZEN','FENCED','APPROVED','COMPLETED','ABORTED')),
 reason varchar(300) NOT NULL CHECK(length(btrim(reason))>=5),
 fence_evidence_ref varchar(160), fence_last_version bigint, fenced_by uuid,
 proposed_by uuid NOT NULL, approved_by uuid, completed_epoch bigint, abort_reason varchar(300),
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,head_id) REFERENCES synqoria_offline.record_heads(cell_id,facility_id,id),
 CONSTRAINT transfer_checker_independent CHECK(approved_by IS NULL OR approved_by<>proposed_by),
 CONSTRAINT fenced_has_evidence CHECK(status NOT IN ('FENCED','APPROVED','COMPLETED') OR (fence_evidence_ref IS NOT NULL AND fence_last_version IS NOT NULL AND fenced_by IS NOT NULL)),
 CONSTRAINT approved_has_approver CHECK(status NOT IN ('APPROVED','COMPLETED') OR approved_by IS NOT NULL),
 CONSTRAINT completed_epoch_increases CHECK((status='COMPLETED')=(completed_epoch IS NOT NULL) AND (completed_epoch IS NULL OR completed_epoch=from_epoch+1)));
-- at most ONE open transfer per record: no dual ownership
CREATE UNIQUE INDEX one_open_transfer ON synqoria_offline.ownership_transfers (cell_id,facility_id,head_id) WHERE status IN ('PROPOSED','FROZEN','FENCED','APPROVED');

-- ---------------------------------------------------------------- B06/S05: reconciliation history (one immutable row per terminal outcome)
CREATE TABLE synqoria_offline.reconciliations(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, command_row uuid, transfer_id uuid, head_id uuid NOT NULL,
 outcome varchar(24) NOT NULL CHECK(outcome IN ('APPLIED','APPLIED_MERGED','REJECTED','EXPIRED','RESOLVED_LOCAL','RESOLVED_AUTHORITATIVE','OWNERSHIP_TRANSFERRED')),
 reviewer uuid, evidence jsonb NOT NULL CHECK(jsonb_typeof(evidence)='object'), final_version bigint NOT NULL, final_epoch bigint NOT NULL,
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 CONSTRAINT has_subject CHECK(command_row IS NOT NULL OR transfer_id IS NOT NULL));
CREATE INDEX recon_time ON synqoria_offline.reconciliations (cell_id,facility_id,occurred_at DESC);

-- ---------------------------------------------------------------- idempotency, audit, outbox
CREATE TABLE synqoria_offline.command_idempotency(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, actor_id uuid NOT NULL, command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'), response jsonb, http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id), CHECK((response IS NULL)=(http_status IS NULL)));
CREATE TABLE synqoria_offline.audit_events(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, aggregate_type varchar(24) NOT NULL, aggregate_id uuid NOT NULL,
 action varchar(48) NOT NULL, from_status varchar(20), to_status varchar(20) NOT NULL, aggregate_version bigint NOT NULL CHECK(aggregate_version>=1),
 actor_id uuid NOT NULL, command_id uuid NOT NULL, details jsonb NOT NULL CHECK(jsonb_typeof(details)='object'),
 digest char(64) NOT NULL CHECK(digest ~ '^[0-9a-f]{64}$'), occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id));
CREATE INDEX audit_aggregate ON synqoria_offline.audit_events (cell_id,facility_id,aggregate_type,aggregate_id,occurred_at,event_id);
CREATE TABLE synqoria_offline.outbox(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, event_type varchar(64) NOT NULL, aggregate_id uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_offline.audit_events(cell_id,facility_id,event_id));
CREATE INDEX outbox_unpublished ON synqoria_offline.outbox (created_at,event_id) WHERE published_at IS NULL;

-- ---------------------------------------------------------------- immutability and invariants
CREATE FUNCTION synqoria_offline.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN RAISE EXCEPTION 'm38_immutable_record: % on %.% is not permitted',TG_OP,TG_TABLE_SCHEMA,TG_TABLE_NAME USING ERRCODE='integrity_constraint_violation'; END $t$;
DO $imm$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['audit_events','reconciliations','record_revisions'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON synqoria_offline.%I FOR EACH ROW EXECUTE FUNCTION synqoria_offline.forbid_mutation()',rel||'_immutable',rel);
  EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON synqoria_offline.%I FOR EACH STATEMENT EXECUTE FUNCTION synqoria_offline.forbid_mutation()',rel||'_no_truncate',rel);
 END LOOP;
END $imm$;
-- a received command is evidence: only its handling status may move, never its content
CREATE FUNCTION synqoria_offline.protect_command() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR to_jsonb(NEW)-'status'-'outcome_code'-'applied_version'-'version' IS DISTINCT FROM to_jsonb(OLD)-'status'-'outcome_code'-'applied_version'-'version'
    OR (OLD.status IN ('APPLIED','REJECTED','EXPIRED','RESOLVED') AND NEW.status IS DISTINCT FROM OLD.status)
    OR (OLD.status='CONFLICT' AND NEW.status NOT IN ('CONFLICT','RESOLVED'))
 THEN RAISE EXCEPTION 'm38_immutable_record: command envelopes are evidence' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER commands_protect BEFORE UPDATE OR DELETE ON synqoria_offline.commands FOR EACH ROW EXECUTE FUNCTION synqoria_offline.protect_command();
-- a resolved conflict is an immutable decision
CREATE FUNCTION synqoria_offline.protect_conflict() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR OLD.status='RESOLVED'
    OR to_jsonb(NEW)-'status'-'resolution'-'reason_code'-'reason_note'-'resolved_by'-'resolved_at'-'resulting_version'-'version' IS DISTINCT FROM
       to_jsonb(OLD)-'status'-'resolution'-'reason_code'-'reason_note'-'resolved_by'-'resolved_at'-'resulting_version'-'version'
 THEN RAISE EXCEPTION 'm38_immutable_record: conflict decisions are fixed' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER conflicts_protect BEFORE UPDATE OR DELETE ON synqoria_offline.conflicts FOR EACH ROW EXECUTE FUNCTION synqoria_offline.protect_conflict();
-- ownership epoch is monotonic; the owner may change only together with an epoch increase of exactly one; versions never go backwards
CREATE FUNCTION synqoria_offline.protect_head() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR NEW.record_type IS DISTINCT FROM OLD.record_type OR NEW.record_key IS DISTINCT FROM OLD.record_key
    OR NEW.ownership_epoch<OLD.ownership_epoch OR NEW.head_version<OLD.head_version
    OR (NEW.owner_ref IS DISTINCT FROM OLD.owner_ref AND NEW.ownership_epoch<>OLD.ownership_epoch+1)
    OR (NEW.ownership_epoch>OLD.ownership_epoch AND (NEW.ownership_epoch<>OLD.ownership_epoch+1 OR NEW.owner_ref IS NOT DISTINCT FROM OLD.owner_ref))
 THEN RAISE EXCEPTION 'm38_epoch_violation: ownership epochs are monotonic and tied to owner change' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER heads_protect BEFORE UPDATE OR DELETE ON synqoria_offline.record_heads FOR EACH ROW EXECUTE FUNCTION synqoria_offline.protect_head();
-- approved contracts are immutable apart from status; transfer states follow a fixed machine
CREATE FUNCTION synqoria_offline.protect_contract() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR (OLD.status<>'DRAFT' AND to_jsonb(NEW)-'status'-'approved_by'-'version' IS DISTINCT FROM to_jsonb(OLD)-'status'-'approved_by'-'version')
    OR (NEW.status<>OLD.status AND NOT ((OLD.status='DRAFT' AND NEW.status IN ('APPROVED','REJECTED')) OR (OLD.status='APPROVED' AND NEW.status='RETIRED')))
 THEN RAISE EXCEPTION 'm38_invalid_transition: contract' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER contracts_protect BEFORE UPDATE OR DELETE ON synqoria_offline.contracts FOR EACH ROW EXECUTE FUNCTION synqoria_offline.protect_contract();
CREATE FUNCTION synqoria_offline.protect_transfer() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR NEW.head_id IS DISTINCT FROM OLD.head_id OR NEW.from_owner IS DISTINCT FROM OLD.from_owner OR NEW.to_owner IS DISTINCT FROM OLD.to_owner
    OR NEW.proposed_by IS DISTINCT FROM OLD.proposed_by OR NEW.from_epoch IS DISTINCT FROM OLD.from_epoch
    OR (NEW.status<>OLD.status AND NOT ((OLD.status='PROPOSED' AND NEW.status IN ('FROZEN','ABORTED')) OR (OLD.status='FROZEN' AND NEW.status IN ('FENCED','ABORTED'))
        OR (OLD.status='FENCED' AND NEW.status IN ('APPROVED','ABORTED')) OR (OLD.status='APPROVED' AND NEW.status IN ('COMPLETED','ABORTED'))))
    OR (OLD.status IN ('COMPLETED','ABORTED') AND NEW IS DISTINCT FROM OLD)
 THEN RAISE EXCEPTION 'm38_invalid_transition: ownership transfer' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER transfers_protect BEFORE UPDATE OR DELETE ON synqoria_offline.ownership_transfers FOR EACH ROW EXECUTE FUNCTION synqoria_offline.protect_transfer();
-- the device cursor and queue limit can only move forwards; a revoked device never comes back
CREATE FUNCTION synqoria_offline.protect_device() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR NEW.last_seq<OLD.last_seq OR NEW.bound_user IS DISTINCT FROM OLD.bound_user OR NEW.device_key IS DISTINCT FROM OLD.device_key
    OR (OLD.status='REVOKED' AND NEW.status<>'REVOKED')
 THEN RAISE EXCEPTION 'm38_immutable_record: device identity and cursor' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER devices_protect BEFORE UPDATE OR DELETE ON synqoria_offline.devices FOR EACH ROW EXECUTE FUNCTION synqoria_offline.protect_device();

-- ---------------------------------------------------------------- grants and forced RLS
GRANT USAGE ON SCHEMA synqoria_offline TO synqoria_offline_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_offline.contracts, synqoria_offline.record_heads, synqoria_offline.devices, synqoria_offline.commands, synqoria_offline.conflicts,
 synqoria_offline.ownership_transfers, synqoria_offline.command_idempotency TO synqoria_offline_runtime;
GRANT SELECT,INSERT ON synqoria_offline.record_revisions, synqoria_offline.reconciliations, synqoria_offline.audit_events, synqoria_offline.outbox TO synqoria_offline_runtime;
DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['contracts','record_heads','record_revisions','devices','commands','conflicts','ownership_transfers','reconciliations','command_idempotency','audit_events','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_offline.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_offline.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('CREATE POLICY offline_scoped_access ON synqoria_offline.%I FOR ALL TO synqoria_offline_runtime '||
   'USING (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) '||
   'WITH CHECK (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;
COMMIT;
