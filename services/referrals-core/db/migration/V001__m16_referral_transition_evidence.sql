BEGIN;
DO $guard$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_referral_runtime'
  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication)
 THEN RAISE EXCEPTION 'm16_restricted_referral_role_required';END IF;
END $guard$;
CREATE SCHEMA synqoria_referral;REVOKE ALL ON SCHEMA synqoria_referral FROM PUBLIC;
CREATE TABLE synqoria_referral.authority_proofs(
 proof_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 patient_ref uuid NOT NULL,referral_id uuid,
 recipient_facility uuid NOT NULL,kind varchar(24) NOT NULL
 CHECK(kind IN('PATIENT_CONSENT','RECEIVING_DECISION','HANDOVER_RECEIPT','FOLLOWUP_VERIFICATION')),
 external_actor uuid NOT NULL,issuer varchar(120) NOT NULL,
 valid_from timestamptz NOT NULL,valid_until timestamptz NOT NULL,
 revoked_at timestamptz,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 digest_sha256 char(64) NOT NULL CHECK(digest_sha256 ~ '^[0-9a-f]{64}$'),
 UNIQUE(cell_id,facility_id,proof_id),
 CHECK(valid_until>valid_from),
 CHECK((kind='PATIENT_CONSENT')=(referral_id IS NULL))
);
CREATE TABLE synqoria_referral.referrals(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 patient_ref uuid NOT NULL,recipient_facility uuid NOT NULL,
 reason_code varchar(64) NOT NULL CHECK(reason_code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
 summary_sha256 char(64) NOT NULL CHECK(summary_sha256 ~ '^[0-9a-f]{64}$'),
 consent_proof_id uuid NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'DRAFT'
 CHECK(status IN('DRAFT','AUTHORIZED','SENT','ACCEPTED','REJECTED','TRANSFERRED','ATTENDED','CLOSED','CANCELLED')),
 version bigint NOT NULL DEFAULT 1 CHECK(version>=1),
 creator_id uuid NOT NULL,authorized_by uuid,sent_by uuid,acceptance_actor uuid,
 transfer_actor uuid,attended_by uuid,closed_by uuid,
 receiving_proof_id uuid,handover_proof_id uuid,followup_proof_id uuid,
 decision varchar(12),decision_at timestamptz,transferred_at timestamptz,
 attended_at timestamptz,closed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,consent_proof_id) REFERENCES synqoria_referral.authority_proofs(cell_id,facility_id,proof_id),
 FOREIGN KEY(cell_id,facility_id,receiving_proof_id) REFERENCES synqoria_referral.authority_proofs(cell_id,facility_id,proof_id),
 FOREIGN KEY(cell_id,facility_id,handover_proof_id) REFERENCES synqoria_referral.authority_proofs(cell_id,facility_id,proof_id),
 FOREIGN KEY(cell_id,facility_id,followup_proof_id) REFERENCES synqoria_referral.authority_proofs(cell_id,facility_id,proof_id),
 CHECK(recipient_facility<>facility_id),
 CHECK(status NOT IN('AUTHORIZED','SENT','ACCEPTED','REJECTED','TRANSFERRED','ATTENDED','CLOSED')
  OR authorized_by IS NOT NULL),
 CHECK(status NOT IN('SENT','ACCEPTED','REJECTED','TRANSFERRED','ATTENDED','CLOSED') OR sent_by IS NOT NULL),
 CHECK(status NOT IN('ACCEPTED','REJECTED','TRANSFERRED','ATTENDED','CLOSED') OR receiving_proof_id IS NOT NULL),
 CHECK(status NOT IN('TRANSFERRED','ATTENDED','CLOSED') OR handover_proof_id IS NOT NULL),
 CHECK(status NOT IN('ATTENDED','CLOSED') OR attended_at IS NOT NULL),
 CHECK(status<>'CLOSED' OR (closed_at IS NOT NULL AND followup_proof_id IS NOT NULL)),
 CHECK(creator_id<>COALESCE(authorized_by,'00000000-0000-0000-0000-000000000000'::uuid)),
 CHECK(authorized_by IS NULL OR sent_by IS NULL OR authorized_by<>sent_by)
);
CREATE INDEX m16_patient_referrals ON synqoria_referral.referrals
 (cell_id,facility_id,patient_ref,created_at DESC);
CREATE INDEX m16_open_referrals ON synqoria_referral.referrals
 (cell_id,facility_id,status,created_at DESC);
CREATE TABLE synqoria_referral.transition_events(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 referral_id uuid NOT NULL,from_status varchar(16) NOT NULL,to_status varchar(16) NOT NULL,
 actor_id uuid NOT NULL,version bigint NOT NULL CHECK(version>=1),proof_id uuid,
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,referral_id) REFERENCES synqoria_referral.referrals(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,referral_id,version)
);
CREATE TABLE synqoria_referral.commands(
 cell_id uuid NOT NULL,facility_id uuid NOT NULL,actor_id uuid NOT NULL,command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 response jsonb,http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id),
 CHECK((response IS NULL)=(http_status IS NULL))
);
CREATE TABLE synqoria_referral.audit_events(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 aggregate_id uuid NOT NULL,action varchar(48) NOT NULL,
 version bigint NOT NULL,actor_id uuid NOT NULL,command_id uuid NOT NULL,
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE synqoria_referral.outbox(
 id uuid PRIMARY KEY,cell_id uuid NOT NULL,facility_id uuid NOT NULL,
 aggregate_id uuid NOT NULL,event_type varchar(80) NOT NULL,payload jsonb NOT NULL
 CHECK(jsonb_typeof(payload)='object'),created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(id) REFERENCES synqoria_referral.audit_events(id)
);
CREATE FUNCTION synqoria_referral.immutable() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN RAISE EXCEPTION 'm16_immutable_history' USING ERRCODE='23514';END $f$;
CREATE TRIGGER m16_transition_immutable BEFORE UPDATE OR DELETE ON synqoria_referral.transition_events
 FOR EACH ROW EXECUTE FUNCTION synqoria_referral.immutable();
CREATE TRIGGER m16_audit_immutable BEFORE UPDATE OR DELETE ON synqoria_referral.audit_events
 FOR EACH ROW EXECUTE FUNCTION synqoria_referral.immutable();
CREATE TRIGGER m16_outbox_immutable BEFORE UPDATE OR DELETE ON synqoria_referral.outbox
 FOR EACH ROW EXECUTE FUNCTION synqoria_referral.immutable();
CREATE FUNCTION synqoria_referral.proof_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm16_proof_delete_denied' USING ERRCODE='23514';END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(OLD.proof_id::text,16));
 IF ROW(NEW.proof_id,NEW.cell_id,NEW.facility_id,NEW.patient_ref,NEW.referral_id,
 NEW.recipient_facility,NEW.kind,NEW.external_actor,NEW.issuer,NEW.valid_from,
 NEW.created_at,NEW.digest_sha256)
 IS DISTINCT FROM ROW(OLD.proof_id,OLD.cell_id,OLD.facility_id,OLD.patient_ref,OLD.referral_id,
 OLD.recipient_facility,OLD.kind,OLD.external_actor,OLD.issuer,OLD.valid_from,
 OLD.created_at,OLD.digest_sha256)
 OR NEW.valid_until>OLD.valid_until OR (OLD.revoked_at IS NOT NULL AND
 NEW.revoked_at IS DISTINCT FROM OLD.revoked_at)
 THEN RAISE EXCEPTION 'm16_proof_rebind_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m16_proof_guard BEFORE UPDATE OR DELETE ON synqoria_referral.authority_proofs
 FOR EACH ROW EXECUTE FUNCTION synqoria_referral.proof_guard();
CREATE FUNCTION synqoria_referral.referral_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm16_referral_delete_denied' USING ERRCODE='23514';END IF;
 IF NEW.version<>OLD.version+1 OR ROW(NEW.id,NEW.cell_id,NEW.facility_id,NEW.patient_ref,
 NEW.recipient_facility,NEW.reason_code,NEW.summary_sha256,NEW.consent_proof_id,
 NEW.creator_id,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.cell_id,OLD.facility_id,OLD.patient_ref,
 OLD.recipient_facility,OLD.reason_code,OLD.summary_sha256,OLD.consent_proof_id,
 OLD.creator_id,OLD.created_at)
 OR NOT(
 (OLD.status='DRAFT' AND NEW.status IN('AUTHORIZED','CANCELLED')) OR
 (OLD.status='AUTHORIZED' AND NEW.status IN('SENT','CANCELLED')) OR
 (OLD.status='SENT' AND NEW.status IN('ACCEPTED','REJECTED')) OR
 (OLD.status='ACCEPTED' AND NEW.status='TRANSFERRED') OR
 (OLD.status='TRANSFERRED' AND NEW.status='ATTENDED') OR
 (OLD.status='ATTENDED' AND NEW.status='CLOSED')
 ) THEN RAISE EXCEPTION 'm16_invalid_referral_transition' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER m16_referral_guard BEFORE UPDATE OR DELETE ON synqoria_referral.referrals
 FOR EACH ROW EXECUTE FUNCTION synqoria_referral.referral_guard();
DO $policy$ DECLARE tbl text;BEGIN
 FOREACH tbl IN ARRAY ARRAY['authority_proofs','referrals','transition_events',
  'commands','audit_events','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_referral.%I ENABLE ROW LEVEL SECURITY',tbl);
  EXECUTE format('ALTER TABLE synqoria_referral.%I FORCE ROW LEVEL SECURITY',tbl);
  EXECUTE format('CREATE POLICY scope ON synqoria_referral.%I USING
  (cell_id::text=current_setting(''synqoria.cell'',true)
   AND facility_id::text=current_setting(''synqoria.facility'',true))
  WITH CHECK(cell_id::text=current_setting(''synqoria.cell'',true)
   AND facility_id::text=current_setting(''synqoria.facility'',true))',tbl);
 END LOOP;
END $policy$;
GRANT USAGE ON SCHEMA synqoria_referral TO synqoria_referral_runtime;
GRANT SELECT ON ALL TABLES IN SCHEMA synqoria_referral TO synqoria_referral_runtime;
GRANT INSERT,UPDATE ON synqoria_referral.referrals,synqoria_referral.commands TO synqoria_referral_runtime;
GRANT INSERT ON synqoria_referral.transition_events,synqoria_referral.audit_events,
 synqoria_referral.outbox TO synqoria_referral_runtime;
REVOKE ALL ON FUNCTION synqoria_referral.immutable() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_referral.proof_guard() FROM PUBLIC;
REVOKE ALL ON FUNCTION synqoria_referral.referral_guard() FROM PUBLIC;
COMMIT;