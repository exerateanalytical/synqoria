-- SYNQORIA P03 — standalone identity-cell schema candidate.
-- This is intentionally NOT assigned an existing clinical-core V### migration.
-- Deploy through the reviewed identity-DB migration pipeline only.
-- Requires a dedicated no-bypass identity service role with explicit, narrowly
-- scoped INSERT and DELETE privileges. No clinical/patient columns.
CREATE SCHEMA IF NOT EXISTS synqoria_identity;

CREATE TABLE IF NOT EXISTS synqoria_identity.oidc_replay_state (
    state_key varchar(92) PRIMARY KEY,
    expires_at timestamptz NOT NULL,
    CONSTRAINT oidc_replay_state_key_format
      CHECK (state_key ~ '^synqoria:oidc:state:v1:[0-9a-f]{64}$')
);

-- Sensitive authorization cookies, authorization codes, access tokens,
-- refresh tokens and patient identifiers MUST NEVER be stored here.
-- State identifiers are SHA-256 digests, not the raw OAuth state.

CREATE INDEX IF NOT EXISTS oidc_replay_state_expiry_idx
  ON synqoria_identity.oidc_replay_state (expires_at);

-- Security posture: RLS fail-closed even for table-owner access.
ALTER TABLE synqoria_identity.oidc_replay_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_identity.oidc_replay_state FORCE ROW LEVEL SECURITY;

-- No policy is installed by this schema: a reviewed deployment must create
-- explicit least-privilege policies for the separate identity service role.
-- Without such a policy, all INSERT/DELETE will be rejected by RLS.
-- Identity role must have NO BYPASSRLS and must not own this table.
-- Expired rows require a separately reviewed cleanup role/job, not a bypass.
