-- P06 M02 draft migration; QUARANTINE ONLY. No application grants, no clinical release.
-- Requires a DBA-approved btree_gist extension and a reviewed restricted DB role.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS synqoria_appointment_reservations_quarantine (
  appointment_id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  patient_id uuid NOT NULL,
  resource_id uuid NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  time_zone text NOT NULL,
  lifecycle text NOT NULL CHECK (lifecycle IN ('REQUESTED','CONFIRMED','ARRIVED','FULFILLED','CANCELLED','NO_SHOW')),
  revision bigint NOT NULL CHECK (revision > 0),
  last_command_digest text CHECK (last_command_digest ~ '^[0-9a-f]{64}$'),
  CONSTRAINT appointment_positive_window CHECK (ends_at > starts_at),
  CONSTRAINT appointment_bounded_window CHECK (ends_at <= starts_at + interval '24 hours'),
  CONSTRAINT appointment_confirmed_no_overlap EXCLUDE USING gist (
    tenant_id WITH =,
    facility_id WITH =,
    resource_id WITH =,
    tstzrange(starts_at,ends_at,'[)') WITH &&
  ) WHERE (lifecycle = 'CONFIRMED') DEFERRABLE INITIALLY IMMEDIATE
);
ALTER TABLE synqoria_appointment_reservations_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_appointment_reservations_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_appointment_reservations_quarantine FROM PUBLIC;
-- No RLS policy or app-role grant until trusted session settings and patient-scope
-- predicates have been reviewed and proven under a non-owner, non-BYPASSRLS role.
-- All production writes remain disabled.
