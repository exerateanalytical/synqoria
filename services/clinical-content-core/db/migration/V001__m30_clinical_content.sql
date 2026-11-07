-- SYNQORIA M30 Terminology, formularies and clinical content governance.
-- Apply with a separate DATABASE MIGRATION role, never the runtime login.
-- Provision synqoria_content_runtime (LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEROLE NOCREATEDB NOREPLICATION) first.
-- Boundaries: M30 owns terminology, formulary reference entries and rule packages. It never writes prescriptions,
-- orders, dispensing or patient records. Rule execution is advisory. Content is inactive until an activation
-- record exists, which requires independent clinical + policy approvals.
BEGIN;

DO $guard$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_content_runtime'
  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole
  AND NOT rolcreatedb AND NOT rolreplication AND NOT rolinherit)
 THEN RAISE EXCEPTION 'missing independent restricted synqoria_content_runtime';
 END IF;
END $guard$;

CREATE SCHEMA IF NOT EXISTS synqoria_content;
REVOKE ALL ON SCHEMA synqoria_content FROM PUBLIC;

-- ------------------------------------------------------------------ terminology
CREATE TABLE synqoria_content.code_systems(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 system_uri varchar(300) NOT NULL CHECK(system_uri ~ '^[a-z][a-z0-9+.-]*:[A-Za-z0-9._~:/%-]{2,255}$'),
 name varchar(200) NOT NULL CHECK(length(btrim(name)) > 0),
 licensing_class varchar(20) NOT NULL CHECK(licensing_class IN ('OPEN','INTERNAL','LICENSED_RESTRICTED')),
 license_ref varchar(200),
 created_by uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,system_uri),
 -- restricted terminology may only be held with a recorded licence
 CONSTRAINT restricted_has_license CHECK(licensing_class <> 'LICENSED_RESTRICTED' OR (license_ref IS NOT NULL AND length(btrim(license_ref)) > 0))
);

CREATE TABLE synqoria_content.code_system_versions(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 code_system_id uuid NOT NULL,
 version_label varchar(64) NOT NULL CHECK(version_label ~ '^[A-Za-z0-9._-]{1,64}$'),
 status varchar(12) NOT NULL CHECK(status IN ('PUBLISHED','REJECTED')),
 content_checksum char(64) NOT NULL CHECK(content_checksum ~ '^[0-9a-f]{64}$'),
 declared_checksum char(64) NOT NULL CHECK(declared_checksum ~ '^[0-9a-f]{64}$'),
 concept_count int NOT NULL CHECK(concept_count >= 0),
 effective_from timestamptz NOT NULL,
 provenance varchar(500) NOT NULL CHECK(length(btrim(provenance)) > 0),
 rejection_codes text[] NOT NULL DEFAULT '{}',
 imported_by uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,code_system_id) REFERENCES synqoria_content.code_systems(cell_id,facility_id,id),
 CONSTRAINT published_checksum_verified CHECK(status <> 'PUBLISHED' OR content_checksum = declared_checksum),
 CONSTRAINT rejected_has_reasons CHECK((status = 'REJECTED') = (cardinality(rejection_codes) > 0))
);
-- a published version label is permanent; rejected attempts are kept but never block a corrected re-import
CREATE UNIQUE INDEX csv_published_label ON synqoria_content.code_system_versions
 (cell_id,facility_id,code_system_id,version_label) WHERE status = 'PUBLISHED';
CREATE INDEX csv_system ON synqoria_content.code_system_versions (cell_id,facility_id,code_system_id,effective_from DESC);

CREATE TABLE synqoria_content.concepts(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 version_id uuid NOT NULL,
 code varchar(64) NOT NULL CHECK(code ~ '^[A-Za-z0-9._-]{1,64}$'),
 status varchar(10) NOT NULL CHECK(status IN ('ACTIVE','INACTIVE','DEPRECATED')),
 display_en varchar(500) NOT NULL CHECK(length(btrim(display_en)) > 0),
 display_fr varchar(500),
 parent_code varchar(64),
 UNIQUE(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,version_id,code),
 FOREIGN KEY(cell_id,facility_id,version_id) REFERENCES synqoria_content.code_system_versions(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,version_id,parent_code) REFERENCES synqoria_content.concepts(cell_id,facility_id,version_id,code)
  DEFERRABLE INITIALLY DEFERRED,
 CHECK(parent_code IS NULL OR parent_code <> code)
);
CREATE INDEX concepts_parent ON synqoria_content.concepts (cell_id,facility_id,version_id,parent_code);
CREATE INDEX concepts_search_en ON synqoria_content.concepts (cell_id,facility_id,version_id,lower(display_en) text_pattern_ops);

CREATE TABLE synqoria_content.concept_synonyms(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 concept_id uuid NOT NULL,
 synonym varchar(300) NOT NULL CHECK(length(btrim(synonym)) > 0),
 PRIMARY KEY(cell_id,facility_id,concept_id,synonym),
 FOREIGN KEY(cell_id,facility_id,concept_id) REFERENCES synqoria_content.concepts(cell_id,facility_id,id)
);

CREATE TABLE synqoria_content.concept_mappings(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 source_version_id uuid NOT NULL,
 source_code varchar(64) NOT NULL,
 target_version_id uuid NOT NULL,
 target_code varchar(64) NOT NULL,
 relation varchar(12) NOT NULL CHECK(relation IN ('EQUIVALENT','NARROWER','BROADER','RELATED')),
 status varchar(10) NOT NULL CHECK(status IN ('PROPOSED','APPROVED','REJECTED')),
 proposed_by uuid NOT NULL,
 decided_by uuid,
 decided_at timestamptz,
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,source_version_id,source_code) REFERENCES synqoria_content.concepts(cell_id,facility_id,version_id,code),
 FOREIGN KEY(cell_id,facility_id,target_version_id,target_code) REFERENCES synqoria_content.concepts(cell_id,facility_id,version_id,code),
 CONSTRAINT mapping_checker_independent CHECK(decided_by IS NULL OR decided_by <> proposed_by),
 CONSTRAINT mapping_decision_atomic CHECK((status = 'PROPOSED') = (decided_by IS NULL))
);
-- one approved equivalent target per source concept and target system version: no conflicting mappings
CREATE UNIQUE INDEX mapping_one_equivalent ON synqoria_content.concept_mappings
 (cell_id,facility_id,source_version_id,source_code,target_version_id) WHERE status = 'APPROVED' AND relation = 'EQUIVALENT';

-- ------------------------------------------------------------------ formulary
CREATE TABLE synqoria_content.formulary_entries(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 entry_key varchar(64) NOT NULL CHECK(entry_key ~ '^[A-Za-z0-9._-]{1,64}$'),
 version_no int NOT NULL CHECK(version_no >= 1),
 product_version_id uuid NOT NULL,
 product_code varchar(64) NOT NULL,
 formulation varchar(80) NOT NULL CHECK(length(btrim(formulation)) > 0),
 route varchar(40) NOT NULL CHECK(route ~ '^[A-Z0-9_]{2,40}$'),
 permitted_use varchar(500) NOT NULL CHECK(length(btrim(permitted_use)) > 0),
 restrictions text[] NOT NULL DEFAULT '{}' CHECK(cardinality(restrictions) <= 20),
 status varchar(12) NOT NULL CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','SUPERSEDED','WITHDRAWN')),
 supersedes_id uuid,
 author_id uuid NOT NULL,
 approver_id uuid,
 approved_at timestamptz,
 decision_note varchar(300),
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,entry_key,version_no),
 FOREIGN KEY(cell_id,facility_id,product_version_id,product_code) REFERENCES synqoria_content.concepts(cell_id,facility_id,version_id,code),
 FOREIGN KEY(cell_id,facility_id,supersedes_id) REFERENCES synqoria_content.formulary_entries(cell_id,facility_id,id),
 CONSTRAINT formulary_approver_independent CHECK(approver_id IS NULL OR approver_id <> author_id),
 CONSTRAINT formulary_decided CHECK((status IN ('APPROVED','SUPERSEDED','REJECTED')) = (approver_id IS NOT NULL) OR status IN ('WITHDRAWN'))
);
CREATE UNIQUE INDEX formulary_one_approved ON synqoria_content.formulary_entries
 (cell_id,facility_id,entry_key) WHERE status = 'APPROVED';
CREATE UNIQUE INDEX formulary_one_open ON synqoria_content.formulary_entries
 (cell_id,facility_id,entry_key) WHERE status IN ('DRAFT','SUBMITTED');

-- ------------------------------------------------------------------ clinical rule packages
CREATE TABLE synqoria_content.rule_packages(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 package_key varchar(64) NOT NULL CHECK(package_key ~ '^[A-Za-z0-9._-]{1,64}$'),
 version_no int NOT NULL CHECK(version_no >= 1),
 status varchar(18) NOT NULL CHECK(status IN
  ('DRAFT','VALIDATED','VALIDATION_FAILED','IN_REVIEW','APPROVED','REJECTED','REVOKED','RETIRED')),
 definition jsonb NOT NULL CHECK(jsonb_typeof(definition)='object' AND octet_length(definition::text) <= 524288),
 definition_hash char(64) NOT NULL CHECK(definition_hash ~ '^[0-9a-f]{64}$'),
 source_attribution varchar(500) NOT NULL CHECK(length(btrim(source_attribution)) > 0),
 depends_on jsonb NOT NULL DEFAULT '[]'::jsonb CHECK(jsonb_typeof(depends_on)='array'),
 effective_from timestamptz NOT NULL,
 effective_to timestamptz,
 previous_id uuid,
 author_id uuid NOT NULL,
 validated_hash char(64),
 version bigint NOT NULL CHECK(version >= 1),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 UNIQUE(cell_id,facility_id,package_key,version_no),
 FOREIGN KEY(cell_id,facility_id,previous_id) REFERENCES synqoria_content.rule_packages(cell_id,facility_id,id),
 CHECK(effective_to IS NULL OR effective_to > effective_from),
 -- review states can only be reached from a validated, hash-bound definition
 CONSTRAINT reviewed_is_validated CHECK(status NOT IN ('IN_REVIEW','APPROVED','REVOKED','RETIRED') OR validated_hash = definition_hash)
);
CREATE TABLE synqoria_content.golden_cases(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 package_id uuid NOT NULL,
 case_name varchar(120) NOT NULL CHECK(length(btrim(case_name)) > 0),
 facts jsonb NOT NULL CHECK(jsonb_typeof(facts)='object'),
 expected jsonb NOT NULL CHECK(jsonb_typeof(expected)='object'),
 UNIQUE(cell_id,facility_id,package_id,case_name),
 FOREIGN KEY(cell_id,facility_id,package_id) REFERENCES synqoria_content.rule_packages(cell_id,facility_id,id)
);
CREATE TABLE synqoria_content.validation_runs(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 package_id uuid NOT NULL,
 definition_hash char(64) NOT NULL,
 outcome varchar(8) NOT NULL CHECK(outcome IN ('PASSED','FAILED')),
 report jsonb NOT NULL CHECK(jsonb_typeof(report)='object'),
 run_by uuid NOT NULL,
 run_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(cell_id,facility_id,package_id) REFERENCES synqoria_content.rule_packages(cell_id,facility_id,id)
);
CREATE INDEX validation_package ON synqoria_content.validation_runs (cell_id,facility_id,package_id,run_at DESC);

-- ------------------------------------------------------------------ approvals (append-only; only revocation may be set later)
CREATE TABLE synqoria_content.approvals(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 package_id uuid NOT NULL,
 definition_hash char(64) NOT NULL,
 role varchar(10) NOT NULL CHECK(role IN ('CLINICAL','POLICY')),
 decision varchar(8) NOT NULL CHECK(decision IN ('APPROVE','REJECT')),
 reviewer_id uuid NOT NULL,
 reason varchar(500) NOT NULL CHECK(length(btrim(reason)) > 0),
 decided_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 expires_at timestamptz NOT NULL,
 revoked_at timestamptz,
 revoked_by uuid,
 revoke_reason varchar(500),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,package_id) REFERENCES synqoria_content.rule_packages(cell_id,facility_id,id),
 CHECK(expires_at > decided_at),
 CONSTRAINT revoke_atomic CHECK((revoked_at IS NULL) = (revoked_by IS NULL AND revoke_reason IS NULL))
);
-- a reviewer gives at most one live decision per package and role; no reviewer fills both roles
CREATE UNIQUE INDEX approvals_one_per_role ON synqoria_content.approvals (cell_id,facility_id,package_id,role) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX approvals_one_per_reviewer ON synqoria_content.approvals (cell_id,facility_id,package_id,reviewer_id) WHERE revoked_at IS NULL;

-- ------------------------------------------------------------------ activation
CREATE TABLE synqoria_content.activations(
 id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 subject_type varchar(12) NOT NULL CHECK(subject_type IN ('RULE_PACKAGE','FORMULARY','CODE_SYSTEM')),
 subject_key varchar(300) NOT NULL,
 subject_id uuid NOT NULL,
 status varchar(12) NOT NULL CHECK(status IN ('ACTIVE','SUPERSEDED','ROLLED_BACK','SUSPENDED')),
 activated_by uuid NOT NULL,
 activated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 previous_id uuid,
 end_reason varchar(300),
 impact jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(impact)='object'),
 version bigint NOT NULL CHECK(version >= 1),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,previous_id) REFERENCES synqoria_content.activations(cell_id,facility_id,id),
 CONSTRAINT ended_has_reason CHECK((status = 'ACTIVE') = (end_reason IS NULL))
);
-- atomic, exclusive activation: exactly one ACTIVE version per subject in a facility
CREATE UNIQUE INDEX activation_one_active ON synqoria_content.activations
 (cell_id,facility_id,subject_type,subject_key) WHERE status = 'ACTIVE';
CREATE INDEX activation_history ON synqoria_content.activations (cell_id,facility_id,subject_type,subject_key,activated_at DESC);

-- ------------------------------------------------------------------ idempotency, audit, outbox
CREATE TABLE synqoria_content.command_idempotency(
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
CREATE TABLE synqoria_content.audit_events(
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
CREATE INDEX audit_aggregate ON synqoria_content.audit_events (cell_id,facility_id,aggregate_type,aggregate_id,occurred_at,event_id);
CREATE TABLE synqoria_content.outbox(
 event_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 event_type varchar(64) NOT NULL,
 aggregate_id uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_content.audit_events(cell_id,facility_id,event_id)
);
CREATE INDEX outbox_unpublished ON synqoria_content.outbox (created_at,event_id) WHERE published_at IS NULL;

-- ------------------------------------------------------------------ immutability
CREATE FUNCTION synqoria_content.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 RAISE EXCEPTION 'm30_immutable_record: % on %.% is not permitted', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME
  USING ERRCODE = 'integrity_constraint_violation';
END $t$;
-- published terminology, golden cases, validation runs and audit are append-only
DO $imm$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['audit_events','concepts','concept_synonyms','code_system_versions','golden_cases','validation_runs'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON synqoria_content.%I FOR EACH ROW EXECUTE FUNCTION synqoria_content.forbid_mutation()',rel||'_immutable',rel);
  EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON synqoria_content.%I FOR EACH STATEMENT EXECUTE FUNCTION synqoria_content.forbid_mutation()',rel||'_no_truncate',rel);
 END LOOP;
END $imm$;

-- rule package definitions are immutable once leaving DRAFT; hash must match; terminal states are final
CREATE FUNCTION synqoria_content.protect_package() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP = 'DELETE' THEN
  RAISE EXCEPTION 'm30_immutable_record: rule packages are never deleted' USING ERRCODE='integrity_constraint_violation';
 END IF;
 IF OLD.status <> 'DRAFT' AND (NEW.definition IS DISTINCT FROM OLD.definition OR NEW.definition_hash IS DISTINCT FROM OLD.definition_hash
    OR NEW.package_key IS DISTINCT FROM OLD.package_key OR NEW.version_no IS DISTINCT FROM OLD.version_no
    OR NEW.effective_from IS DISTINCT FROM OLD.effective_from OR NEW.effective_to IS DISTINCT FROM OLD.effective_to
    OR NEW.depends_on IS DISTINCT FROM OLD.depends_on OR NEW.author_id IS DISTINCT FROM OLD.author_id
    OR NEW.source_attribution IS DISTINCT FROM OLD.source_attribution)
 THEN RAISE EXCEPTION 'm30_immutable_record: package content is immutable after draft' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.status <> OLD.status AND NOT (
    (OLD.status = 'DRAFT' AND NEW.status IN ('VALIDATED','VALIDATION_FAILED'))
    OR (OLD.status = 'VALIDATED' AND NEW.status = 'IN_REVIEW')
    OR (OLD.status = 'IN_REVIEW' AND NEW.status IN ('APPROVED','REJECTED','REVOKED'))
    OR (OLD.status = 'APPROVED' AND NEW.status IN ('REVOKED','RETIRED')))
 THEN RAISE EXCEPTION 'm30_invalid_transition: package % -> %', OLD.status, NEW.status USING ERRCODE='integrity_constraint_violation'; END IF;
 -- approval is only reachable with live clinical AND policy sign-offs from two different, non-author reviewers
 IF NEW.status = 'APPROVED' AND OLD.status <> 'APPROVED' AND (
    SELECT count(DISTINCT a.role) FROM synqoria_content.approvals a
     WHERE a.package_id = NEW.id AND a.decision = 'APPROVE' AND a.revoked_at IS NULL
       AND a.definition_hash = NEW.definition_hash AND a.reviewer_id <> NEW.author_id) <> 2
 THEN RAISE EXCEPTION 'm30_approval_incomplete: package needs clinical and policy sign-off' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER rule_packages_protect BEFORE UPDATE OR DELETE ON synqoria_content.rule_packages
 FOR EACH ROW EXECUTE FUNCTION synqoria_content.protect_package();

-- approvals: the decision itself is immutable; only revocation columns may be set, once
CREATE FUNCTION synqoria_content.protect_approval() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP = 'DELETE' OR OLD.revoked_at IS NOT NULL
    OR NEW.package_id IS DISTINCT FROM OLD.package_id OR NEW.definition_hash IS DISTINCT FROM OLD.definition_hash
    OR NEW.role IS DISTINCT FROM OLD.role OR NEW.decision IS DISTINCT FROM OLD.decision
    OR NEW.reviewer_id IS DISTINCT FROM OLD.reviewer_id OR NEW.reason IS DISTINCT FROM OLD.reason
    OR NEW.decided_at IS DISTINCT FROM OLD.decided_at OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
 THEN RAISE EXCEPTION 'm30_immutable_record: approval records are immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER approvals_protect BEFORE UPDATE OR DELETE ON synqoria_content.approvals
 FOR EACH ROW EXECUTE FUNCTION synqoria_content.protect_approval();

-- activations: never deleted; the subject recorded cannot change
CREATE FUNCTION synqoria_content.protect_activation() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP = 'DELETE' OR NEW.subject_id IS DISTINCT FROM OLD.subject_id OR NEW.subject_key IS DISTINCT FROM OLD.subject_key
    OR NEW.subject_type IS DISTINCT FROM OLD.subject_type OR NEW.activated_by IS DISTINCT FROM OLD.activated_by
    OR NEW.activated_at IS DISTINCT FROM OLD.activated_at OR (OLD.status <> 'ACTIVE' AND NEW.status <> OLD.status)
 THEN RAISE EXCEPTION 'm30_immutable_record: activation history is immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER activations_protect BEFORE UPDATE OR DELETE ON synqoria_content.activations
 FOR EACH ROW EXECUTE FUNCTION synqoria_content.protect_activation();

-- ------------------------------------------------------------------ grants and forced RLS
GRANT USAGE ON SCHEMA synqoria_content TO synqoria_content_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_content.concept_mappings, synqoria_content.formulary_entries,
 synqoria_content.rule_packages, synqoria_content.approvals, synqoria_content.activations,
 synqoria_content.command_idempotency TO synqoria_content_runtime;
GRANT SELECT,INSERT ON synqoria_content.code_systems, synqoria_content.code_system_versions, synqoria_content.concepts,
 synqoria_content.concept_synonyms, synqoria_content.golden_cases, synqoria_content.validation_runs,
 synqoria_content.audit_events, synqoria_content.outbox TO synqoria_content_runtime;

DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['code_systems','code_system_versions','concepts','concept_synonyms','concept_mappings','formulary_entries',
  'rule_packages','golden_cases','validation_runs','approvals','activations','command_idempotency','audit_events','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_content.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_content.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('CREATE POLICY content_scoped_access ON synqoria_content.%I FOR ALL TO synqoria_content_runtime '||
   'USING (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) '||
   'WITH CHECK (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;
COMMIT;
