-- SYNQORIA P03-B0046 — restricted expired staff-session janitor.
-- Separates expired-ticket deletion from authentication runtime. No janitor
-- SELECT of encrypted records, no UPDATE and no visibility into live tickets.
-- Apply only after services/identity/db/staff_sessions_v1.sql.
BEGIN;
DO $roles$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_staff_session_janitor') THEN
  CREATE ROLE synqoria_staff_session_janitor NOLOGIN NOINHERIT NOBYPASSRLS;
 END IF;
END
$roles$;

REVOKE ALL ON TABLE synqoria_identity.staff_sessions FROM PUBLIC;
GRANT USAGE ON SCHEMA synqoria_identity TO synqoria_staff_session_janitor;
GRANT SELECT (session_digest,expires_at) ON synqoria_identity.staff_sessions
 TO synqoria_staff_session_janitor;
GRANT DELETE ON synqoria_identity.staff_sessions
 TO synqoria_staff_session_janitor;

ALTER TABLE synqoria_identity.staff_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_identity.staff_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY staff_session_janitor_read_expired
 ON synqoria_identity.staff_sessions FOR SELECT
 TO synqoria_staff_session_janitor
 USING (expires_at <= clock_timestamp());
CREATE POLICY staff_session_janitor_delete_expired
 ON synqoria_identity.staff_sessions FOR DELETE
 TO synqoria_staff_session_janitor
 USING (expires_at <= clock_timestamp());
COMMIT;
