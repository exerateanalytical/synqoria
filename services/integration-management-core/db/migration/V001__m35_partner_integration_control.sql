-- M35 is the management/control plane for external connections.
-- It never returns usable API secrets, touches raw clinical payloads, or initiates network replays.
BEGIN;
DO $guard$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_integration_runtime'
  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication)
 THEN RAISE EXCEPTION 'm35_restricted_runtime_role_required'; END IF;
END $guard$;
CREATE SCHEMA synqoria_integration;
REVOKE ALL ON SCHEMA synqoria_integration FROM PUBLIC;

CREATE TABLE synqoria_integration.partner_proofs(
 proof_id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 partner_code varchar(64) NOT NULL CHECK(partner_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 proof_type varchar(24) NOT NULL CHECK(proof_type IN('PARTNER_KYB','SOURCE_MESSAGE')),
 source_event_id uuid,source_payload_sha256 char(64)
 CHECK(source_payload_sha256 ~ '^[0-9a-f]{64}$'),
 external_actor uuid NOT NULL,issuer varchar(120) NOT NULL,
 valid_from timestamptz NOT NULL,valid_until timestamptz NOT NULL,
 revoked_at timestamptz,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,proof_id),
 CHECK(valid_until>valid_from),
 CHECK((proof_type='SOURCE_MESSAGE')=(source_event_id IS NOT NULL AND source_payload_sha256 IS NOT NULL))
);
CREATE TABLE synqoria_integration.partners(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 partner_code varchar(64) NOT NULL CHECK(partner_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 partner_type varchar(24) NOT NULL CHECK(partner_type IN('HOSPITAL','LABORATORY','PAYER','GOVERNMENT','VENDOR')),
 status varchar(16) NOT NULL DEFAULT 'PENDING'
 CHECK(status IN('PENDING','VERIFIED','ENABLED','SUSPENDED','RETIRED')),
 proof_id uuid,version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_by uuid NOT NULL,verified_by uuid,enabled_by uuid,suspended_by uuid,retired_by uuid,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),UNIQUE(cell_id,facility_id,partner_code),
 FOREIGN KEY(cell_id,facility_id,proof_id) REFERENCES synqoria_integration.partner_proofs(cell_id,facility_id,proof_id),
 CHECK(verified_by IS NULL OR verified_by<>created_by),
 CHECK(enabled_by IS NULL OR enabled_by<>created_by)
);
CREATE TABLE synqoria_integration.api_products(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 api_code varchar(64) NOT NULL CHECK(api_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 api_version varchar(64) NOT NULL CHECK(api_version ~ '^[A-Z0-9][A-Z0-9_.-]{0,63}$'),
 contract_sha256 char(64) NOT NULL CHECK(contract_sha256 ~ '^[0-9a-f]{64}$'),
 status varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK(status IN('DRAFT','PUBLISHED','RETIRED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_by uuid NOT NULL,published_by uuid,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),UNIQUE(cell_id,facility_id,api_code,api_version),
 CHECK(published_by IS NULL OR published_by<>created_by)
);
CREATE TABLE synqoria_integration.developer_apps(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 partner_id uuid NOT NULL,app_code varchar(64) NOT NULL CHECK(app_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 api_product_id uuid NOT NULL,status varchar(14) NOT NULL DEFAULT 'REQUESTED'
 CHECK(status IN('REQUESTED','APPROVED','SUSPENDED')),
 credential_status varchar(16) NOT NULL DEFAULT 'NOT_ISSUED' CHECK(credential_status='NOT_ISSUED'),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 requested_by uuid NOT NULL,reviewed_by uuid,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 reviewed_at timestamptz,
 UNIQUE(cell_id,facility_id,id),UNIQUE(cell_id,facility_id,partner_id,app_code),
 FOREIGN KEY(cell_id,facility_id,partner_id) REFERENCES synqoria_integration.partners(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,api_product_id) REFERENCES synqoria_integration.api_products(cell_id,facility_id,id),
 CHECK(reviewed_by IS NULL OR reviewed_by<>requested_by)
);
CREATE TABLE synqoria_integration.mapping_versions(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 map_code varchar(64) NOT NULL CHECK(map_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 map_version varchar(64) NOT NULL,contract_sha256 char(64) NOT NULL CHECK(contract_sha256 ~ '^[0-9a-f]{64}$'),
 source_system varchar(64) NOT NULL,target_system varchar(64) NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK(status IN('DRAFT','VALIDATED','APPROVED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 created_by uuid NOT NULL,validated_by uuid,approved_by uuid,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),UNIQUE(cell_id,facility_id,map_code,map_version),
 CHECK(validated_by IS NULL OR validated_by<>created_by),
 CHECK(approved_by IS NULL OR (approved_by<>created_by AND approved_by<>validated_by))
);
CREATE TABLE synqoria_integration.messages(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 partner_id uuid NOT NULL,external_event_id uuid NOT NULL,
 event_type varchar(64) NOT NULL CHECK(event_type ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 payload_sha256 char(64) NOT NULL CHECK(payload_sha256 ~ '^[0-9a-f]{64}$'),
 source_proof_id uuid NOT NULL,status varchar(16) NOT NULL DEFAULT 'QUARANTINED'
 CHECK(status IN('QUARANTINED','ACCEPTED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 received_by uuid NOT NULL,reviewed_by uuid,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),UNIQUE(cell_id,facility_id,partner_id,external_event_id),
 FOREIGN KEY(cell_id,facility_id,partner_id) REFERENCES synqoria_integration.partners(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,source_proof_id) REFERENCES synqoria_integration.partner_proofs(cell_id,facility_id,proof_id),
 CHECK(reviewed_by IS NULL OR reviewed_by<>received_by)
);
CREATE TABLE synqoria_integration.replay_requests(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 message_id uuid NOT NULL,reason_code varchar(64) NOT NULL
 CHECK(reason_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 status varchar(24) NOT NULL DEFAULT 'REQUESTED'
 CHECK(status IN('REQUESTED','APPROVED_PENDING_DELIVERY','DENIED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 requested_by uuid NOT NULL,reviewed_by uuid,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),reviewed_at timestamptz,
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,message_id) REFERENCES synqoria_integration.messages(cell_id,facility_id,id),
 CHECK(reviewed_by IS NULL OR reviewed_by<>requested_by)
);
CREATE TABLE synqoria_integration.commands(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,actor_id uuid NOT NULL,
 command_id uuid NOT NULL,request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 response jsonb,http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id),
 CHECK((response IS NULL)=(http_status IS NULL))
);
CREATE TABLE synqoria_integration.audit_events(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 type varchar(24) NOT NULL,aggregate_id uuid NOT NULL,action varchar(64) NOT NULL,
 version bigint NOT NULL,actor_id uuid NOT NULL,command_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE synqoria_integration.outbox(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 aggregate_id uuid NOT NULL,event_type varchar(120) NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(id) REFERENCES synqoria_integration.audit_events(id)
);
CREATE FUNCTION synqoria_integration.immutable() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN RAISE EXCEPTION 'm35_immutable_audit' USING ERRCODE='23514';END $f$;
CREATE TRIGGER m35_audit_immutable BEFORE UPDATE OR DELETE ON synqoria_integration.audit_events
 FOR EACH ROW EXECUTE FUNCTION synqoria_integration.immutable();
CREATE TRIGGER m35_outbox_immutable BEFORE UPDATE OR DELETE ON synqoria_integration.outbox
 FOR EACH ROW EXECUTE FUNCTION synqoria_integration.immutable();
CREATE FUNCTION synqoria_integration.proof_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm35_proof_delete_denied' USING ERRCODE='23514';END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(OLD.proof_id::text,35));
 IF ROW(NEW.proof_id,NEW.cell_id,NEW.facility_id,NEW.partner_code,NEW.proof_type,
  NEW.source_event_id,NEW.source_payload_sha256,NEW.external_actor,NEW.issuer,
  NEW.valid_from,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.proof_id,OLD.cell_id,OLD.facility_id,OLD.partner_code,OLD.proof_type,
  OLD.source_event_id,OLD.source_payload_sha256,OLD.external_actor,OLD.issuer,
  OLD.valid_from,OLD.created_at)
 OR NEW.valid_until>OLD.valid_until OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at)
 THEN RAISE EXCEPTION 'm35_proof_rebinding_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m35_proof_guard BEFORE UPDATE OR DELETE ON synqoria_integration.partner_proofs
 FOR EACH ROW EXECUTE FUNCTION synqoria_integration.proof_guard();
DO $policy$ DECLARE tbl text;BEGIN
 FOREACH tbl IN ARRAY ARRAY['partner_proofs','partners','api_products','developer_apps',
  'mapping_versions','messages','replay_requests','commands','audit_events','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_integration.%I ENABLE ROW LEVEL SECURITY',tbl);
  EXECUTE format('ALTER TABLE synqoria_integration.%I FORCE ROW LEVEL SECURITY',tbl);
  EXECUTE format('CREATE POLICY hospital_scope ON synqoria_integration.%I USING
   (cell_id::text=current_setting(''synqoria.cell'',true)
    AND facility_id::text=current_setting(''synqoria.facility'',true))
   WITH CHECK(cell_id::text=current_setting(''synqoria.cell'',true)
    AND facility_id::text=current_setting(''synqoria.facility'',true))',tbl);
 END LOOP;
END $policy$;
GRANT USAGE ON SCHEMA synqoria_integration TO synqoria_integration_runtime;
GRANT SELECT ON ALL TABLES IN SCHEMA synqoria_integration TO synqoria_integration_runtime;
GRANT INSERT,UPDATE ON synqoria_integration.partners,synqoria_integration.api_products,
 synqoria_integration.developer_apps,synqoria_integration.mapping_versions,
 synqoria_integration.messages,synqoria_integration.replay_requests,
 synqoria_integration.commands TO synqoria_integration_runtime;
GRANT INSERT ON synqoria_integration.audit_events,synqoria_integration.outbox TO synqoria_integration_runtime;
REVOKE ALL ON FUNCTION synqoria_integration.immutable() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_integration.proof_guard() FROM PUBLIC;
COMMIT;