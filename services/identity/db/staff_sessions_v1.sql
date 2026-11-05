-- SYNQORIA P03-B0037 — identity-only, encrypted staff session persistence.
-- Requires PR #26's synqoria_identity schema. Does NOT store PHI or plaintext
-- access tokens. Authorized server stores only salted-ticket entropy digest and
-- a separately AES-256-GCM sealed record. Do not deploy without DBA review.
BEGIN;

CREATE TABLE IF NOT EXISTS synqoria_identity.staff_sessions (
 session_digest char(64) PRIMARY KEY,
 sealed_record varchar(19000) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 expires_at timestamptz NOT NULL,
 CONSTRAINT session_digest_hex CHECK (session_digest ~ '^[0-9a-f]{64}$'),
 CONSTRAINT session_encrypted_v1 CHECK (sealed_record ~ '^v1[.][A-Za-z0-9_-]+[.][A-Za-z0-9_-]+[.][A-Za-z0-9_-]+$'),
 CONSTRAINT session_ttl CHECK (expires_at > created_at AND expires_at <= created_at + INTERVAL '10 minutes')
);
CREATE INDEX IF NOT EXISTS staff_sessions_expiry_idx
 ON synqoria_identity.staff_sessions(expires_at);

DO $roles$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_staff_session_runtime') THEN
   CREATE ROLE synqoria_staff_session_runtime NOLOGIN NOINHERIT NOBYPASSRLS;
 END IF;
END
$roles$;

REVOKE ALL ON TABLE synqoria_identity.staff_sessions FROM PUBLIC;
GRANT USAGE ON SCHEMA synqoria_identity TO synqoria_staff_session_runtime;
GRANT INSERT ON TABLE synqoria_identity.staff_sessions TO synqoria_staff_session_runtime;
GRANT SELECT (session_digest,sealed_record,expires_at)
 ON TABLE synqoria_identity.staff_sessions TO synqoria_staff_session_runtime;
GRANT DELETE ON TABLE synqoria_identity.staff_sessions TO synqoria_staff_session_runtime;

ALTER TABLE synqoria_identity.staff_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_identity.staff_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY staff_session_active_read
 ON synqoria_identity.staff_sessions FOR SELECT TO synqoria_staff_session_runtime
 USING (expires_at > clock_timestamp());
CREATE POLICY staff_session_new_secure
 ON synqoria_identity.staff_sessions FOR INSERT TO synqoria_staff_session_runtime
 WITH CHECK (expires_at > clock_timestamp()
  AND expires_at <= clock_timestamp() + INTERVAL '10 minutes');
CREATE POLICY staff_session_revoke_once
 ON synqoria_identity.staff_sessions FOR DELETE TO synqoria_staff_session_runtime
 USING (expires_at > clock_timestamp());

COMMIT;
