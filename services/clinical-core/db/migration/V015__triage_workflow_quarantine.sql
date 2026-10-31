-- M03 P06 triage persistence design. QUARANTINE ONLY.
-- No application grants or RLS policies: not deployable to operational roles until clinical/security review.
-- Clinical text in metadata and observations requires reviewed encryption, retention and access design.
CREATE TABLE IF NOT EXISTS synqoria_triage_cases_quarantine (
  id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  appointment_id uuid,
  station_id uuid,
  assignee_id uuid,
  stage text NOT NULL CHECK (stage IN ('ARRIVED','WAITING','CALLED','ASSESSING','ASSESSED','PRIORITIZED','ASSIGNED','HANDED_OVER','CLOSED')),
  revision bigint NOT NULL CHECK (revision >= 1),
  priority_code text,
  safety_hold boolean NOT NULL DEFAULT false,
  paused boolean NOT NULL DEFAULT false,
  signed_assessment boolean NOT NULL DEFAULT false,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(metadata)='object'),
  observations jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(observations)='object'),
  arrived_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT synqoria_triage_scope_unique UNIQUE (tenant_id,facility_id,patient_id,id),
  CONSTRAINT synqoria_triage_case_id_unique UNIQUE (id)
);
CREATE TABLE IF NOT EXISTS synqoria_triage_events_quarantine (
  tenant_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  case_id uuid NOT NULL,
  command_id uuid NOT NULL,
  actor_id uuid NOT NULL,
  revision bigint NOT NULL CHECK (revision > 1),
  event_type text NOT NULL CHECK (char_length(event_type) BETWEEN 1 AND 80),
  previous_digest text NOT NULL CHECK (previous_digest ~ '^[0-9a-f]{64}$'),
  state_digest text NOT NULL CHECK (state_digest ~ '^[0-9a-f]{64}$'),
  event_digest text NOT NULL CHECK (event_digest ~ '^[0-9a-f]{64}$'),
  occurred_at timestamptz NOT NULL,
  CONSTRAINT triage_event_revision_unique UNIQUE (tenant_id,facility_id,patient_id,case_id,revision),
  CONSTRAINT triage_event_command_unique UNIQUE (tenant_id,facility_id,patient_id,case_id,command_id),
  CONSTRAINT triage_event_case FOREIGN KEY (tenant_id,facility_id,patient_id,case_id)
    REFERENCES synqoria_triage_cases_quarantine (tenant_id,facility_id,patient_id,id)
);
CREATE TABLE IF NOT EXISTS synqoria_triage_outbox_quarantine (
  event_id uuid NOT NULL,
  tenant_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  case_id uuid NOT NULL,
  command_id uuid NOT NULL,
  event_digest text NOT NULL CHECK (event_digest ~ '^[0-9a-f]{64}$'),
  occurred_at timestamptz NOT NULL,
  CONSTRAINT triage_outbox_event_unique UNIQUE (event_id),
  CONSTRAINT triage_outbox_command_unique UNIQUE (tenant_id,facility_id,patient_id,case_id,command_id),
  CONSTRAINT triage_outbox_event_chain FOREIGN KEY (tenant_id,facility_id,patient_id,case_id,command_id)
    REFERENCES synqoria_triage_events_quarantine (tenant_id,facility_id,patient_id,case_id,command_id),
  CONSTRAINT triage_outbox_case FOREIGN KEY (tenant_id,facility_id,patient_id,case_id)
    REFERENCES synqoria_triage_cases_quarantine (tenant_id,facility_id,patient_id,id)
);
CREATE INDEX triage_cases_station_worklist_quarantine ON synqoria_triage_cases_quarantine
  (tenant_id,facility_id,station_id,stage,arrived_at) WHERE stage NOT IN ('CLOSED','HANDED_OVER');
CREATE INDEX triage_cases_assignee_worklist_quarantine ON synqoria_triage_cases_quarantine
  (tenant_id,facility_id,assignee_id,stage,arrived_at) WHERE stage NOT IN ('CLOSED','HANDED_OVER');
ALTER TABLE synqoria_triage_cases_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_cases_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_cases_quarantine FROM PUBLIC;
ALTER TABLE synqoria_triage_events_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_events_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_events_quarantine FROM PUBLIC;
ALTER TABLE synqoria_triage_outbox_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_triage_outbox_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_triage_outbox_quarantine FROM PUBLIC;
-- No CREATE POLICY, no application role grants, no BYPASSRLS. Clinical release remains denied.
