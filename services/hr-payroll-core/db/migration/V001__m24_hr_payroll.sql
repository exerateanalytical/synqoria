-- SYNQORIA M24 Human resources, staffing and payroll.
-- Apply with a separate DATABASE MIGRATION role, never the runtime login.
-- Provision synqoria_hr_runtime (LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT NOCREATEROLE NOCREATEDB NOREPLICATION) first.
-- Boundaries: P03 is the identity/grant authority (staff.user_ref is only an opaque reference; HR cannot issue
-- clinical privileges). Payroll rates and statutory rules are DATA entered and approved per country/employer;
-- nothing is hard-coded. Payment state is only ever CONFIRMED by independent external evidence.
-- Bank details are never stored: only an opaque vault token (account_token).
BEGIN;
DO $guard$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_hr_runtime' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls
  AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication AND NOT rolinherit)
 THEN RAISE EXCEPTION 'missing independent restricted synqoria_hr_runtime'; END IF;
END $guard$;
CREATE SCHEMA IF NOT EXISTS synqoria_hr;
REVOKE ALL ON SCHEMA synqoria_hr FROM PUBLIC;

-- ------------------------------------------------------------------ organisation and workforce (B01)
CREATE TABLE synqoria_hr.departments(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 code varchar(32) NOT NULL CHECK(code ~ '^[A-Z0-9_-]{1,32}$'), name varchar(120) NOT NULL CHECK(length(btrim(name))>0),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,code));
CREATE TABLE synqoria_hr.positions(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, department_id uuid NOT NULL,
 code varchar(32) NOT NULL CHECK(code ~ '^[A-Z0-9_-]{1,32}$'), title varchar(120) NOT NULL CHECK(length(btrim(title))>0),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,code),
 FOREIGN KEY(cell_id,facility_id,department_id) REFERENCES synqoria_hr.departments(cell_id,facility_id,id));

CREATE TABLE synqoria_hr.staff(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 staff_number varchar(32) NOT NULL CHECK(staff_number ~ '^[A-Z0-9_-]{1,32}$'),
 given_name varchar(120) NOT NULL CHECK(length(btrim(given_name))>0), family_name varchar(120) NOT NULL CHECK(length(btrim(family_name))>0),
 status varchar(12) NOT NULL CHECK(status IN ('ONBOARDING','ACTIVE','SUSPENDED','TERMINATED')),
 classification varchar(12) NOT NULL CHECK(classification IN ('STANDARD','CONFIDENTIAL')),
 department_id uuid NOT NULL, position_id uuid NOT NULL,
 user_ref uuid,                      -- opaque P03 identity reference; carries no privileges
 hire_date date NOT NULL, termination_date date,
 last_effective_date date NOT NULL,
 version bigint NOT NULL CHECK(version>=1),
 created_by uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,staff_number),
 FOREIGN KEY(cell_id,facility_id,department_id) REFERENCES synqoria_hr.departments(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,position_id) REFERENCES synqoria_hr.positions(cell_id,facility_id,id),
 CHECK((status='TERMINATED')=(termination_date IS NOT NULL)),
 CHECK(termination_date IS NULL OR termination_date>=hire_date),
 CHECK(last_effective_date>=hire_date));
CREATE INDEX staff_search ON synqoria_hr.staff (cell_id,facility_id,department_id,family_name,id);

-- dated, approved change requests (maker-checker); applied atomically
CREATE TABLE synqoria_hr.staff_changes(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, staff_id uuid NOT NULL,
 kind varchar(12) NOT NULL CHECK(kind IN ('ACTIVATE','SUSPEND','REINSTATE','TRANSFER','TERMINATE','REHIRE')),
 effective_date date NOT NULL, to_department_id uuid, to_position_id uuid,
 reason varchar(300) NOT NULL CHECK(length(btrim(reason))>0),
 status varchar(10) NOT NULL CHECK(status IN ('REQUESTED','SCHEDULED','APPLIED','REJECTED')),
 requested_by uuid NOT NULL, decided_by uuid, decided_at timestamptz, applied_at timestamptz,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id),
 CONSTRAINT change_checker_independent CHECK(decided_by IS NULL OR decided_by<>requested_by),
 CONSTRAINT change_decided CHECK((status IN ('REQUESTED'))=(decided_by IS NULL)),
 CONSTRAINT transfer_target CHECK(kind<>'TRANSFER' OR (to_department_id IS NOT NULL AND to_position_id IS NOT NULL)));
CREATE UNIQUE INDEX one_open_change ON synqoria_hr.staff_changes (cell_id,facility_id,staff_id) WHERE status IN ('REQUESTED','SCHEDULED');

CREATE TABLE synqoria_hr.contracts(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, staff_id uuid NOT NULL,
 contract_version int NOT NULL CHECK(contract_version>=1),
 employer_code varchar(32) NOT NULL CHECK(employer_code ~ '^[A-Z0-9_-]{1,32}$'),
 country_code char(2) NOT NULL CHECK(country_code ~ '^[A-Z]{2}$'),
 currency char(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 pay_basis varchar(8) NOT NULL CHECK(pay_basis IN ('MONTHLY','HOURLY')),
 base_amount numeric(18,4) NOT NULL CHECK(base_amount>=0),
 position_id uuid NOT NULL, effective_from date NOT NULL, effective_to date,
 status varchar(10) NOT NULL CHECK(status IN ('DRAFT','ACTIVE','SUPERSEDED','ENDED','REJECTED')),
 created_by uuid NOT NULL, approved_by uuid, approved_at timestamptz,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,staff_id,contract_version),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,position_id) REFERENCES synqoria_hr.positions(cell_id,facility_id,id),
 CHECK(effective_to IS NULL OR effective_to>=effective_from),
 CONSTRAINT contract_checker_independent CHECK(approved_by IS NULL OR approved_by<>created_by),
 CONSTRAINT contract_active_approved CHECK(status NOT IN ('ACTIVE','SUPERSEDED','ENDED') OR approved_by IS NOT NULL));
CREATE UNIQUE INDEX one_active_contract ON synqoria_hr.contracts (cell_id,facility_id,staff_id) WHERE status='ACTIVE';

CREATE TABLE synqoria_hr.payment_accounts(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, staff_id uuid NOT NULL,
 account_token varchar(120) NOT NULL CHECK(length(btrim(account_token))>=8),   -- vault token; never a raw account number
 account_mask varchar(20) NOT NULL, set_by uuid NOT NULL, set_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,staff_id),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id));

-- ------------------------------------------------------------------ rostering (B03)
CREATE TABLE synqoria_hr.shift_templates(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 code varchar(32) NOT NULL CHECK(code ~ '^[A-Z0-9_-]{1,32}$'), start_time time NOT NULL, duration_minutes int NOT NULL CHECK(duration_minutes BETWEEN 30 AND 960),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,code));
CREATE TABLE synqoria_hr.coverage_requirements(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, department_id uuid NOT NULL, template_id uuid NOT NULL,
 min_staff int NOT NULL CHECK(min_staff BETWEEN 0 AND 500),
 PRIMARY KEY(cell_id,facility_id,department_id,template_id),
 FOREIGN KEY(cell_id,facility_id,department_id) REFERENCES synqoria_hr.departments(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,template_id) REFERENCES synqoria_hr.shift_templates(cell_id,facility_id,id));
CREATE TABLE synqoria_hr.rosters(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, department_id uuid NOT NULL,
 period_start date NOT NULL, period_end date NOT NULL, revision int NOT NULL CHECK(revision>=1),
 status varchar(10) NOT NULL CHECK(status IN ('DRAFT','SUBMITTED','PUBLISHED','SUPERSEDED','REJECTED')),
 created_by uuid NOT NULL, reviewed_by uuid, reviewed_at timestamptz, review_note varchar(300),
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,department_id,period_start,revision),
 FOREIGN KEY(cell_id,facility_id,department_id) REFERENCES synqoria_hr.departments(cell_id,facility_id,id),
 CHECK(period_end>=period_start AND period_end-period_start<=62),
 CONSTRAINT roster_reviewer_independent CHECK(reviewed_by IS NULL OR reviewed_by<>created_by),
 CONSTRAINT roster_published_reviewed CHECK(status NOT IN ('PUBLISHED','SUPERSEDED') OR reviewed_by IS NOT NULL));
CREATE UNIQUE INDEX one_published_roster ON synqoria_hr.rosters (cell_id,facility_id,department_id,period_start) WHERE status='PUBLISHED';
CREATE TABLE synqoria_hr.roster_shifts(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, roster_id uuid NOT NULL,
 template_id uuid NOT NULL, staff_id uuid,                       -- NULL staff = vacancy
 starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 overtime boolean NOT NULL DEFAULT false, overtime_authorized_by uuid, overtime_authorized_at timestamptz,
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,roster_id) REFERENCES synqoria_hr.rosters(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,template_id) REFERENCES synqoria_hr.shift_templates(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id),
 CHECK(ends_at>starts_at),
 CONSTRAINT overtime_authorized CHECK(NOT overtime OR overtime_authorized_by IS NOT NULL OR staff_id IS NULL OR true));
CREATE INDEX shifts_staff ON synqoria_hr.roster_shifts (cell_id,facility_id,staff_id,starts_at);
CREATE UNIQUE INDEX shifts_no_dup_start ON synqoria_hr.roster_shifts (cell_id,facility_id,roster_id,staff_id,starts_at) WHERE staff_id IS NOT NULL;

-- ------------------------------------------------------------------ attendance and leave (B04)
CREATE TABLE synqoria_hr.attendance_records(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, staff_id uuid NOT NULL, work_date date NOT NULL,
 clock_in timestamptz NOT NULL, clock_out timestamptz,
 source varchar(8) NOT NULL CHECK(source IN ('DEVICE','IMPORT','MANUAL')),
 import_ref varchar(80),
 status varchar(10) NOT NULL CHECK(status IN ('CAPTURED','DISPUTED','CORRECTED','APPROVED')),
 corrects_id uuid, note varchar(300),
 captured_by uuid NOT NULL, decided_by uuid, decided_at timestamptz,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,corrects_id) REFERENCES synqoria_hr.attendance_records(cell_id,facility_id,id),
 CHECK(clock_out IS NULL OR clock_out>clock_in),
 CONSTRAINT attendance_reviewer_independent CHECK(decided_by IS NULL OR decided_by<>captured_by));
CREATE UNIQUE INDEX attendance_import_once ON synqoria_hr.attendance_records (cell_id,facility_id,source,import_ref) WHERE import_ref IS NOT NULL;
CREATE TABLE synqoria_hr.leave_types(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 code varchar(32) NOT NULL CHECK(code ~ '^[A-Z0-9_-]{1,32}$'), paid boolean NOT NULL,
 accrual_per_month numeric(8,4) NOT NULL CHECK(accrual_per_month>=0), max_balance numeric(8,2) NOT NULL CHECK(max_balance>=0),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,code));
CREATE TABLE synqoria_hr.holidays(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, holiday_date date NOT NULL, name varchar(120) NOT NULL,
 PRIMARY KEY(cell_id,facility_id,holiday_date));
CREATE TABLE synqoria_hr.leave_requests(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, staff_id uuid NOT NULL, leave_type_id uuid NOT NULL,
 from_date date NOT NULL, to_date date NOT NULL, days numeric(6,2) NOT NULL CHECK(days>0),
 status varchar(10) NOT NULL CHECK(status IN ('SUBMITTED','APPROVED','REJECTED','CANCELLED')),
 requested_by uuid NOT NULL, decided_by uuid, decided_at timestamptz, decision_note varchar(300),
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,leave_type_id) REFERENCES synqoria_hr.leave_types(cell_id,facility_id,id),
 CHECK(to_date>=from_date),
 CONSTRAINT leave_checker_independent CHECK(decided_by IS NULL OR decided_by<>requested_by),
 CONSTRAINT leave_decided CHECK((status IN ('SUBMITTED','CANCELLED'))=(decided_by IS NULL) OR status='CANCELLED'));

-- ------------------------------------------------------------------ payroll (B05, B06)
CREATE TABLE synqoria_hr.rule_sets(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL,
 country_code char(2) NOT NULL CHECK(country_code ~ '^[A-Z]{2}$'), employer_code varchar(32) NOT NULL CHECK(employer_code ~ '^[A-Z0-9_-]{1,32}$'),
 rule_version int NOT NULL CHECK(rule_version>=1), currency char(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 effective_from date NOT NULL, rules jsonb NOT NULL CHECK(jsonb_typeof(rules)='object'), rules_hash char(64) NOT NULL CHECK(rules_hash ~ '^[0-9a-f]{64}$'),
 source_reference varchar(300) NOT NULL CHECK(length(btrim(source_reference))>0),      -- e.g. the legal instrument the officer transcribed from
 status varchar(10) NOT NULL CHECK(status IN ('DRAFT','APPROVED','RETIRED','REJECTED')),
 created_by uuid NOT NULL, approved_by uuid, approved_at timestamptz,
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,country_code,employer_code,rule_version),
 CONSTRAINT ruleset_checker_independent CHECK(approved_by IS NULL OR approved_by<>created_by),
 CONSTRAINT ruleset_approved CHECK(status NOT IN ('APPROVED','RETIRED') OR approved_by IS NOT NULL));
CREATE TABLE synqoria_hr.pay_periods(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, employer_code varchar(32) NOT NULL, country_code char(2) NOT NULL, currency char(3) NOT NULL,
 period_start date NOT NULL, period_end date NOT NULL, status varchar(8) NOT NULL CHECK(status IN ('OPEN','LOCKED')),
 version bigint NOT NULL CHECK(version>=1), created_by uuid NOT NULL,
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,employer_code,country_code,period_start),
 CHECK(period_end>=period_start AND period_end-period_start<=31));
CREATE TABLE synqoria_hr.payroll_runs(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, period_id uuid NOT NULL, rule_set_id uuid NOT NULL, run_no int NOT NULL CHECK(run_no>=1),
 status varchar(12) NOT NULL CHECK(status IN ('CALCULATED','SUBMITTED','APPROVED','REJECTED','REVERSED','RECONCILED')),
 currency char(3) NOT NULL, staff_count int NOT NULL CHECK(staff_count>=0), total_gross numeric(20,4) NOT NULL, total_net numeric(20,4) NOT NULL,
 batch_hash char(64) NOT NULL CHECK(batch_hash ~ '^[0-9a-f]{64}$'),
 prepared_by uuid NOT NULL, submitted_at timestamptz, approved_by uuid, approved_at timestamptz, decision_note varchar(300),
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,period_id,run_no),
 FOREIGN KEY(cell_id,facility_id,period_id) REFERENCES synqoria_hr.pay_periods(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,rule_set_id) REFERENCES synqoria_hr.rule_sets(cell_id,facility_id,id),
 CONSTRAINT run_checker_independent CHECK(approved_by IS NULL OR approved_by<>prepared_by),
 CONSTRAINT run_approved CHECK(status NOT IN ('APPROVED','RECONCILED') OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)));
-- one live run per period: re-calculation requires the previous one to be rejected or reversed
CREATE UNIQUE INDEX one_live_run ON synqoria_hr.payroll_runs (cell_id,facility_id,period_id) WHERE status IN ('CALCULATED','SUBMITTED','APPROVED','RECONCILED');
CREATE TABLE synqoria_hr.payroll_lines(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, run_id uuid NOT NULL, staff_id uuid NOT NULL, contract_id uuid NOT NULL,
 currency char(3) NOT NULL, gross numeric(20,4) NOT NULL CHECK(gross>=0), total_deductions numeric(20,4) NOT NULL CHECK(total_deductions>=0), net numeric(20,4) NOT NULL CHECK(net>=0),
 breakdown jsonb NOT NULL CHECK(jsonb_typeof(breakdown)='array'),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,run_id,staff_id),
 FOREIGN KEY(cell_id,facility_id,run_id) REFERENCES synqoria_hr.payroll_runs(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,contract_id) REFERENCES synqoria_hr.contracts(cell_id,facility_id,id),
 CONSTRAINT net_equals CHECK(net=gross-total_deductions));
CREATE TABLE synqoria_hr.pay_adjustments(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, period_id uuid NOT NULL, staff_id uuid NOT NULL,
 kind varchar(12) NOT NULL CHECK(kind IN ('ARREARS','BONUS','CORRECTION','ALLOWANCE','BENEFIT','DEDUCTION')),
 amount numeric(18,4) NOT NULL CHECK(amount>0), reason varchar(300) NOT NULL CHECK(length(btrim(reason))>0),
 requested_by uuid NOT NULL, approved_by uuid, approved_at timestamptz,
 status varchar(10) NOT NULL CHECK(status IN ('REQUESTED','APPROVED','REJECTED')),
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,period_id) REFERENCES synqoria_hr.pay_periods(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id),
 CONSTRAINT adj_checker_independent CHECK(approved_by IS NULL OR approved_by<>requested_by),
 CONSTRAINT adj_decided CHECK((status='REQUESTED')=(approved_by IS NULL)));

-- ------------------------------------------------------------------ payments and reconciliation (B07)
CREATE TABLE synqoria_hr.payment_instructions(
 id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, run_id uuid NOT NULL, staff_id uuid NOT NULL, period_id uuid NOT NULL,
 reference varchar(60) NOT NULL CHECK(reference ~ '^[A-Z0-9-]{8,60}$'),
 amount numeric(20,4) NOT NULL CHECK(amount>0), currency char(3) NOT NULL, account_mask varchar(20) NOT NULL,
 status varchar(10) NOT NULL CHECK(status IN ('INSTRUCTED','CONFIRMED','FAILED','MISMATCH','CANCELLED')),
 instructed_by uuid NOT NULL,
 provider_ref varchar(120), confirmed_amount numeric(20,4), confirmed_by uuid, confirmed_at timestamptz, failure_reason varchar(300),
 version bigint NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,id), UNIQUE(cell_id,facility_id,reference),
 FOREIGN KEY(cell_id,facility_id,run_id) REFERENCES synqoria_hr.payroll_runs(cell_id,facility_id,id),
 FOREIGN KEY(cell_id,facility_id,staff_id) REFERENCES synqoria_hr.staff(cell_id,facility_id,id),
 CONSTRAINT confirm_independent CHECK(confirmed_by IS NULL OR confirmed_by<>instructed_by),
 -- an internal row can only claim CONFIRMED with external evidence recorded by someone else
 CONSTRAINT confirmed_has_evidence CHECK(status<>'CONFIRMED' OR (provider_ref IS NOT NULL AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL AND confirmed_amount=amount)));
-- double-payment guard: at most one live (instructed or confirmed) instruction per staff member per period
CREATE UNIQUE INDEX no_double_payment ON synqoria_hr.payment_instructions (cell_id,facility_id,period_id,staff_id) WHERE status IN ('INSTRUCTED','CONFIRMED','MISMATCH');
CREATE UNIQUE INDEX provider_ref_once ON synqoria_hr.payment_instructions (cell_id,facility_id,provider_ref) WHERE provider_ref IS NOT NULL;

-- ------------------------------------------------------------------ idempotency, audit, outbox
CREATE TABLE synqoria_hr.command_idempotency(
 cell_id uuid NOT NULL, facility_id uuid NOT NULL, actor_id uuid NOT NULL, command_id uuid NOT NULL,
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'), response jsonb, http_status int CHECK(http_status BETWEEN 100 AND 599),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id,command_id), CHECK((response IS NULL)=(http_status IS NULL)));
CREATE TABLE synqoria_hr.audit_events(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, aggregate_type varchar(24) NOT NULL, aggregate_id uuid NOT NULL,
 action varchar(48) NOT NULL, from_status varchar(20), to_status varchar(20) NOT NULL, aggregate_version bigint NOT NULL CHECK(aggregate_version>=1),
 actor_id uuid NOT NULL, command_id uuid NOT NULL, details jsonb NOT NULL CHECK(jsonb_typeof(details)='object'),
 digest char(64) NOT NULL CHECK(digest ~ '^[0-9a-f]{64}$'), occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(cell_id,facility_id,event_id));
CREATE INDEX audit_aggregate ON synqoria_hr.audit_events (cell_id,facility_id,aggregate_type,aggregate_id,occurred_at,event_id);
CREATE TABLE synqoria_hr.outbox(
 event_id uuid PRIMARY KEY, cell_id uuid NOT NULL, facility_id uuid NOT NULL, event_type varchar(64) NOT NULL, aggregate_id uuid NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'), created_at timestamptz NOT NULL DEFAULT clock_timestamp(), published_at timestamptz,
 FOREIGN KEY(cell_id,facility_id,event_id) REFERENCES synqoria_hr.audit_events(cell_id,facility_id,event_id));
CREATE INDEX outbox_unpublished ON synqoria_hr.outbox (created_at,event_id) WHERE published_at IS NULL;

-- ------------------------------------------------------------------ immutability and invariants
CREATE FUNCTION synqoria_hr.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN RAISE EXCEPTION 'm24_immutable_record: % on %.% is not permitted', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE='integrity_constraint_violation'; END $t$;
DO $imm$
DECLARE rel text;
BEGIN
 -- audit, payroll lines and generated results are append-only
 FOREACH rel IN ARRAY ARRAY['audit_events','payroll_lines'] LOOP
  EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE ON synqoria_hr.%I FOR EACH ROW EXECUTE FUNCTION synqoria_hr.forbid_mutation()',rel||'_immutable',rel);
  EXECUTE format('CREATE TRIGGER %I BEFORE TRUNCATE ON synqoria_hr.%I FOR EACH STATEMENT EXECUTE FUNCTION synqoria_hr.forbid_mutation()',rel||'_no_truncate',rel);
 END LOOP;
END $imm$;

-- approved rule sets and approved/locked run results cannot be edited; payroll runs follow a fixed state machine
CREATE FUNCTION synqoria_hr.protect_rule_set() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm24_immutable_record: rule sets are never deleted' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF OLD.status<>'DRAFT' AND (NEW.rules IS DISTINCT FROM OLD.rules OR NEW.rules_hash IS DISTINCT FROM OLD.rules_hash OR NEW.currency IS DISTINCT FROM OLD.currency
   OR NEW.effective_from IS DISTINCT FROM OLD.effective_from OR NEW.country_code IS DISTINCT FROM OLD.country_code OR NEW.employer_code IS DISTINCT FROM OLD.employer_code
   OR NEW.rule_version IS DISTINCT FROM OLD.rule_version OR NEW.created_by IS DISTINCT FROM OLD.created_by)
 THEN RAISE EXCEPTION 'm24_immutable_record: approved rule set content is immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.status<>OLD.status AND NOT ((OLD.status='DRAFT' AND NEW.status IN ('APPROVED','REJECTED')) OR (OLD.status='APPROVED' AND NEW.status='RETIRED'))
 THEN RAISE EXCEPTION 'm24_invalid_transition: rule set % -> %',OLD.status,NEW.status USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER rule_sets_protect BEFORE UPDATE OR DELETE ON synqoria_hr.rule_sets FOR EACH ROW EXECUTE FUNCTION synqoria_hr.protect_rule_set();

CREATE FUNCTION synqoria_hr.protect_run() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm24_immutable_record: payroll runs are never deleted' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.batch_hash IS DISTINCT FROM OLD.batch_hash OR NEW.total_gross IS DISTINCT FROM OLD.total_gross OR NEW.total_net IS DISTINCT FROM OLD.total_net
    OR NEW.rule_set_id IS DISTINCT FROM OLD.rule_set_id OR NEW.period_id IS DISTINCT FROM OLD.period_id OR NEW.prepared_by IS DISTINCT FROM OLD.prepared_by
    OR NEW.staff_count IS DISTINCT FROM OLD.staff_count
 THEN RAISE EXCEPTION 'm24_immutable_record: calculated run results are immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF NEW.status<>OLD.status AND NOT (
    (OLD.status='CALCULATED' AND NEW.status IN ('SUBMITTED','REJECTED'))
    OR (OLD.status='SUBMITTED' AND NEW.status IN ('APPROVED','REJECTED'))
    OR (OLD.status='APPROVED' AND NEW.status IN ('REVERSED','RECONCILED')))
 THEN RAISE EXCEPTION 'm24_invalid_transition: run % -> %',OLD.status,NEW.status USING ERRCODE='integrity_constraint_violation'; END IF;
 IF OLD.status IN ('REJECTED','REVERSED','RECONCILED') AND NEW.status<>OLD.status
 THEN RAISE EXCEPTION 'm24_immutable_record: terminal run status' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER payroll_runs_protect BEFORE UPDATE OR DELETE ON synqoria_hr.payroll_runs FOR EACH ROW EXECUTE FUNCTION synqoria_hr.protect_run();

-- payment evidence is write-once; terminal states are final
CREATE FUNCTION synqoria_hr.protect_instruction() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR NEW.amount IS DISTINCT FROM OLD.amount OR NEW.reference IS DISTINCT FROM OLD.reference OR NEW.staff_id IS DISTINCT FROM OLD.staff_id
    OR NEW.run_id IS DISTINCT FROM OLD.run_id OR NEW.instructed_by IS DISTINCT FROM OLD.instructed_by
    OR (OLD.confirmed_at IS NOT NULL AND (NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at OR NEW.provider_ref IS DISTINCT FROM OLD.provider_ref OR NEW.confirmed_by IS DISTINCT FROM OLD.confirmed_by))
    OR (OLD.status<>'INSTRUCTED' AND NEW.status<>OLD.status)
 THEN RAISE EXCEPTION 'm24_immutable_record: payment instruction evidence is immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER instructions_protect BEFORE UPDATE OR DELETE ON synqoria_hr.payment_instructions FOR EACH ROW EXECUTE FUNCTION synqoria_hr.protect_instruction();

-- contract terms are immutable once decided (new terms = new contract version)
CREATE FUNCTION synqoria_hr.protect_contract() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'm24_immutable_record: contracts are never deleted' USING ERRCODE='integrity_constraint_violation'; END IF;
 IF OLD.status<>'DRAFT' AND (NEW.base_amount IS DISTINCT FROM OLD.base_amount OR NEW.currency IS DISTINCT FROM OLD.currency OR NEW.country_code IS DISTINCT FROM OLD.country_code
    OR NEW.employer_code IS DISTINCT FROM OLD.employer_code OR NEW.pay_basis IS DISTINCT FROM OLD.pay_basis OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
    OR NEW.staff_id IS DISTINCT FROM OLD.staff_id OR NEW.created_by IS DISTINCT FROM OLD.created_by OR NEW.contract_version IS DISTINCT FROM OLD.contract_version)
 THEN RAISE EXCEPTION 'm24_immutable_record: decided contract terms are immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER contracts_protect BEFORE UPDATE OR DELETE ON synqoria_hr.contracts FOR EACH ROW EXECUTE FUNCTION synqoria_hr.protect_contract();


-- approved attendance is final; a correction is a new linked record
CREATE FUNCTION synqoria_hr.protect_attendance() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR OLD.status='APPROVED' OR NEW.clock_in IS DISTINCT FROM OLD.clock_in OR NEW.clock_out IS DISTINCT FROM OLD.clock_out
    OR NEW.staff_id IS DISTINCT FROM OLD.staff_id OR NEW.work_date IS DISTINCT FROM OLD.work_date OR NEW.captured_by IS DISTINCT FROM OLD.captured_by
 THEN RAISE EXCEPTION 'm24_immutable_record: attendance times and approved records are immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER attendance_protect BEFORE UPDATE OR DELETE ON synqoria_hr.attendance_records FOR EACH ROW EXECUTE FUNCTION synqoria_hr.protect_attendance();

-- approved leave decisions are immutable records
CREATE FUNCTION synqoria_hr.protect_leave() RETURNS trigger LANGUAGE plpgsql AS $t$
BEGIN
 IF TG_OP='DELETE' OR (OLD.status IN ('APPROVED','REJECTED') AND NEW.status IS DISTINCT FROM OLD.status)
    OR NEW.days IS DISTINCT FROM OLD.days OR NEW.from_date IS DISTINCT FROM OLD.from_date OR NEW.to_date IS DISTINCT FROM OLD.to_date OR NEW.staff_id IS DISTINCT FROM OLD.staff_id
 THEN RAISE EXCEPTION 'm24_immutable_record: decided leave records are immutable' USING ERRCODE='integrity_constraint_violation'; END IF;
 RETURN NEW;
END $t$;
CREATE TRIGGER leave_protect BEFORE UPDATE OR DELETE ON synqoria_hr.leave_requests FOR EACH ROW EXECUTE FUNCTION synqoria_hr.protect_leave();

-- ------------------------------------------------------------------ grants and forced RLS
GRANT USAGE ON SCHEMA synqoria_hr TO synqoria_hr_runtime;
GRANT SELECT,INSERT,UPDATE ON synqoria_hr.departments, synqoria_hr.positions, synqoria_hr.staff, synqoria_hr.staff_changes, synqoria_hr.contracts,
 synqoria_hr.payment_accounts, synqoria_hr.shift_templates, synqoria_hr.coverage_requirements, synqoria_hr.rosters, synqoria_hr.roster_shifts,
 synqoria_hr.attendance_records, synqoria_hr.leave_types, synqoria_hr.holidays, synqoria_hr.leave_requests, synqoria_hr.rule_sets, synqoria_hr.pay_periods,
 synqoria_hr.payroll_runs, synqoria_hr.pay_adjustments, synqoria_hr.payment_instructions, synqoria_hr.command_idempotency TO synqoria_hr_runtime;
GRANT SELECT,INSERT ON synqoria_hr.payroll_lines, synqoria_hr.audit_events, synqoria_hr.outbox TO synqoria_hr_runtime;
-- roster shifts may be deleted only while their roster is a DRAFT (editing); nothing else is ever deleted
GRANT DELETE ON synqoria_hr.roster_shifts TO synqoria_hr_runtime;

DO $rls$
DECLARE rel text;
BEGIN
 FOREACH rel IN ARRAY ARRAY['departments','positions','staff','staff_changes','contracts','payment_accounts','shift_templates','coverage_requirements','rosters',
  'roster_shifts','attendance_records','leave_types','holidays','leave_requests','rule_sets','pay_periods','payroll_runs','payroll_lines','pay_adjustments',
  'payment_instructions','command_idempotency','audit_events','outbox'] LOOP
  EXECUTE format('ALTER TABLE synqoria_hr.%I ENABLE ROW LEVEL SECURITY',rel);
  EXECUTE format('ALTER TABLE synqoria_hr.%I FORCE ROW LEVEL SECURITY',rel);
  EXECUTE format('CREATE POLICY hr_scoped_access ON synqoria_hr.%I FOR ALL TO synqoria_hr_runtime '||
   'USING (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid) '||
   'WITH CHECK (cell_id = NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid AND facility_id = NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',rel);
 END LOOP;
END $rls$;
-- confidential workforce records are invisible at the database level unless the caller's verified grant includes confidential_read
CREATE POLICY hr_confidential_staff ON synqoria_hr.staff AS RESTRICTIVE FOR SELECT TO synqoria_hr_runtime
 USING (classification='STANDARD' OR current_setting('synqoria.hr_conf',true)='on');
COMMIT;
