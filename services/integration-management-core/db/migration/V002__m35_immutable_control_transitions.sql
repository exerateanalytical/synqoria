-- M35: mutable control-plane state is protected against direct SQL forgery.
BEGIN;
CREATE FUNCTION synqoria_integration.state_guard() RETURNS trigger LANGUAGE plpgsql AS $f$
DECLARE allowed_columns text[]; allowed_state boolean; stage text;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm35_record_delete_denied' USING ERRCODE='23514';END IF;
 IF TG_OP='INSERT' THEN
  stage=CASE TG_TABLE_NAME
   WHEN 'partners' THEN 'PENDING' WHEN 'api_products' THEN 'DRAFT'
   WHEN 'developer_apps' THEN 'REQUESTED' WHEN 'mapping_versions' THEN 'DRAFT'
   WHEN 'messages' THEN 'QUARANTINED' WHEN 'replay_requests' THEN 'REQUESTED'
   END;
  IF NEW.status<>stage OR NEW.version<>1 THEN
   RAISE EXCEPTION 'm35_forged_initial_state' USING ERRCODE='23514'; END IF;
  RETURN NEW;
 END IF;
 IF NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'm35_version_mismatch' USING ERRCODE='23514';END IF;
 CASE TG_TABLE_NAME
 WHEN 'partners' THEN
  allowed_columns=ARRAY['status','version','updated_at','verified_by','enabled_by','suspended_by','retired_by','proof_id'];
  allowed_state=(OLD.status='PENDING' AND NEW.status='VERIFIED')
   OR (OLD.status='VERIFIED' AND NEW.status='ENABLED')
   OR (OLD.status='ENABLED' AND NEW.status='SUSPENDED')
   OR (OLD.status='SUSPENDED' AND NEW.status='RETIRED');
  IF OLD.proof_id IS NOT NULL AND NEW.proof_id IS DISTINCT FROM OLD.proof_id
   THEN RAISE EXCEPTION 'm35_partner_proof_rebinding' USING ERRCODE='23514';END IF;
 WHEN 'api_products' THEN
  allowed_columns=ARRAY['status','version','updated_at','published_by'];
  allowed_state=OLD.status='DRAFT' AND NEW.status='PUBLISHED';
 WHEN 'developer_apps' THEN
  allowed_columns=ARRAY['status','version','reviewed_by','reviewed_at'];
  allowed_state=(OLD.status='REQUESTED' AND NEW.status='APPROVED')
   OR (OLD.status='APPROVED' AND NEW.status='SUSPENDED');
 WHEN 'mapping_versions' THEN
  allowed_columns=ARRAY['status','version','validated_by','approved_by'];
  allowed_state=(OLD.status='DRAFT' AND NEW.status='VALIDATED')
   OR (OLD.status='VALIDATED' AND NEW.status='APPROVED');
 WHEN 'messages' THEN
  allowed_columns=ARRAY['status','version','reviewed_by'];
  allowed_state=OLD.status='QUARANTINED' AND NEW.status='ACCEPTED';
 WHEN 'replay_requests' THEN
  allowed_columns=ARRAY['status','version','reviewed_by','reviewed_at'];
  allowed_state=OLD.status='REQUESTED' AND NEW.status IN('APPROVED_PENDING_DELIVERY','DENIED');
 ELSE RAISE EXCEPTION 'm35_unknown_control_table' USING ERRCODE='23514';
 END CASE;
 IF NOT COALESCE(allowed_state,false) OR
  (to_jsonb(NEW)-allowed_columns) IS DISTINCT FROM (to_jsonb(OLD)-allowed_columns)
 THEN RAISE EXCEPTION 'm35_mutation_denied' USING ERRCODE='23514';END IF;
 RETURN NEW;
END $f$;
DO $tr$ DECLARE name text;BEGIN
 FOREACH name IN ARRAY ARRAY['partners','api_products','developer_apps',
  'mapping_versions','messages','replay_requests'] LOOP
  EXECUTE format('CREATE TRIGGER m35_%I_guard BEFORE INSERT OR UPDATE OR DELETE ON synqoria_integration.%I FOR EACH ROW EXECUTE FUNCTION synqoria_integration.state_guard()',name,name);
 END LOOP;
END $tr$;
REVOKE ALL ON FUNCTION synqoria_integration.state_guard() FROM PUBLIC;
COMMIT;