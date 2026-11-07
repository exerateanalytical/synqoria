-- SYNQORIA P03/GATE-01 — executable identity-DB grants and RLS migration.
--
-- Apply only after oidc_replay_state_candidate.sql with a reviewed deployment
-- owner; intentionally independent of all clinical-core Flyway V### numbers.
-- No patient information, JWTs, secrets, authorization codes or bearer tokens.
--
-- Both NOLOGIN roles require explicitly provisioned identity-service or
-- janitor principals. Never grant these roles to clinical/application users.
-- A privileged DBA must review the role membership and credentials separately.

BEGIN;

DO $role$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'synqoria_oidc_replay_runtime') THEN
    CREATE ROLE synqoria_oidc_replay_runtime NOLOGIN NOINHERIT NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'synqoria_oidc_replay_janitor') THEN
    CREATE ROLE synqoria_oidc_replay_janitor NOLOGIN NOINHERIT NOBYPASSRLS;
  END IF;
END
$role$;

-- Public schema grants are revoked before dedicated service grants.
REVOKE ALL ON SCHEMA synqoria_identity FROM PUBLIC;
REVOKE ALL ON TABLE synqoria_identity.oidc_replay_state FROM PUBLIC;
GRANT USAGE ON SCHEMA synqoria_identity
  TO synqoria_oidc_replay_runtime, synqoria_oidc_replay_janitor;

-- Runtime may only create and consume anonymous state digests.
GRANT INSERT, DELETE ON TABLE synqoria_identity.oidc_replay_state
  TO synqoria_oidc_replay_runtime;
GRANT SELECT (state_key, expires_at)
  ON TABLE synqoria_identity.oidc_replay_state
  TO synqoria_oidc_replay_runtime;

-- Janitor may only delete already expired records and has no insert power.
GRANT DELETE ON TABLE synqoria_identity.oidc_replay_state
  TO synqoria_oidc_replay_janitor;
GRANT SELECT (state_key, expires_at)
  ON TABLE synqoria_identity.oidc_replay_state
  TO synqoria_oidc_replay_janitor;

ALTER TABLE synqoria_identity.oidc_replay_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_identity.oidc_replay_state FORCE ROW LEVEL SECURITY;

CREATE POLICY oidc_replay_runtime_read_live
  ON synqoria_identity.oidc_replay_state
  FOR SELECT TO synqoria_oidc_replay_runtime
  USING (expires_at > clock_timestamp());

CREATE POLICY oidc_replay_runtime_create_live
  ON synqoria_identity.oidc_replay_state
  FOR INSERT TO synqoria_oidc_replay_runtime
  WITH CHECK (
    expires_at > clock_timestamp()
    AND expires_at <= clock_timestamp() + INTERVAL '5 minutes'
  );

CREATE POLICY oidc_replay_runtime_consume_live
  ON synqoria_identity.oidc_replay_state
  FOR DELETE TO synqoria_oidc_replay_runtime
  USING (expires_at > clock_timestamp());

CREATE POLICY oidc_replay_janitor_read_expired
  ON synqoria_identity.oidc_replay_state
  FOR SELECT TO synqoria_oidc_replay_janitor
  USING (expires_at <= clock_timestamp());

CREATE POLICY oidc_replay_janitor_delete_expired
  ON synqoria_identity.oidc_replay_state
  FOR DELETE TO synqoria_oidc_replay_janitor
  USING (expires_at <= clock_timestamp());

COMMIT;
