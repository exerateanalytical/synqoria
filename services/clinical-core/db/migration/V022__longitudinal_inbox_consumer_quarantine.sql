-- Quarantined consumer-inbox persistence. No production role grants or ALLOW policies.
-- Live PostgreSQL/RLS/concurrency verification is REQUIRED before activating any consumer.
CREATE TABLE synqoria_longitudinal_inbox_quarantine (
  tenant_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  encounter_id uuid NOT NULL,
  consumer_id uuid NOT NULL,
  event_id uuid NOT NULL,
  command_id uuid NOT NULL,
  revision bigint NOT NULL CHECK (revision BETWEEN 1 AND 20000),
  event_hash char(64) NOT NULL CHECK (event_hash ~ '^[0-9a-f]{64}$'),
  event_type varchar(96) NOT NULL CHECK (event_type ~ '^[a-z][a-z0-9_.-]{0,95}$'),
  replay_key char(64) NOT NULL CHECK (replay_key ~ '^[0-9a-f]{64}$'),
  state varchar(12) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','CLAIMED','APPLIED','PARKED')),
  lease_token uuid,
  lease_expires_at timestamptz,
  attempts smallint NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 12),
  applied_at timestamptz,
  committed_revision bigint CHECK (committed_revision BETWEEN 1 AND 20000),
  committed_hash char(64) CHECK (committed_hash ~ '^[0-9a-f]{64}$'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY(tenant_id,facility_id,patient_id,encounter_id,consumer_id,event_id),
  UNIQUE(tenant_id,facility_id,patient_id,encounter_id,consumer_id,replay_key),
  UNIQUE(tenant_id,facility_id,patient_id,encounter_id,consumer_id,revision),
  CHECK ((state='CLAIMED' AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
     OR (state<>'CLAIMED' AND lease_token IS NULL AND lease_expires_at IS NULL)),
  CHECK ((state='APPLIED' AND applied_at IS NOT NULL AND committed_revision=revision
          AND committed_hash=event_hash)
     OR (state<>'APPLIED' AND applied_at IS NULL AND committed_revision IS NULL
          AND committed_hash IS NULL))
);
CREATE TABLE synqoria_longitudinal_inbox_cursor_quarantine (
  tenant_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  encounter_id uuid NOT NULL,
  consumer_id uuid NOT NULL,
  last_revision bigint NOT NULL DEFAULT 0 CHECK (last_revision BETWEEN 0 AND 20000),
  last_hash char(64) CHECK (last_hash ~ '^[0-9a-f]{64}$'),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY(tenant_id,facility_id,patient_id,encounter_id,consumer_id),
  CHECK ((last_revision=0 AND last_hash IS NULL) OR (last_revision>0 AND last_hash IS NOT NULL))
);
CREATE INDEX synqoria_longitudinal_inbox_pending_quarantine_idx
  ON synqoria_longitudinal_inbox_quarantine
  (tenant_id,facility_id,patient_id,encounter_id,consumer_id,revision)
  WHERE state IN ('PENDING','CLAIMED');
ALTER TABLE synqoria_longitudinal_inbox_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_inbox_quarantine FORCE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_inbox_cursor_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_inbox_cursor_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_longitudinal_inbox_quarantine FROM PUBLIC;
REVOKE ALL ON synqoria_longitudinal_inbox_cursor_quarantine FROM PUBLIC;
-- Intentionally no CREATE POLICY and no GRANT. A reviewed role migration must precede runtime use.
