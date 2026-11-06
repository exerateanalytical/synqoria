-- P03-B0097 production role reconciliation candidate.
-- Existing synqoria_*_runtime identities deliberately use NOLOGIN and FORCE RLS.
-- Client DSNs must authenticate as distinct NOINHERIT login principals which
-- may SET LOCAL ROLE only to their single matching runtime role.
-- This SQL does not provision passwords, TLS certificates or a secret vault.
-- Review with DBA before applying to real infrastructure.
BEGIN;
DO $principal$
DECLARE rec record;
BEGIN
 FOR rec IN
  SELECT * FROM (VALUES
   ('synqoria_oidc_replay_login','synqoria_oidc_replay_runtime'),
   ('synqoria_staff_session_login','synqoria_staff_session_runtime'),
   ('synqoria_identity_audit_login','synqoria_identity_audit_writer')
  ) AS v(login_name,runtime_role)
 LOOP
  IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=rec.runtime_role
    AND rolcanlogin=false AND rolsuper=false AND rolbypassrls=false) THEN
   RAISE EXCEPTION 'identity runtime roles must exist as restricted NOLOGIN';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=rec.login_name) THEN
   EXECUTE format('CREATE ROLE %I LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD NULL',rec.login_name);
  END IF;
  EXECUTE format('ALTER ROLE %I LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS',rec.login_name);
  EXECUTE format('GRANT %I TO %I WITH ADMIN FALSE, INHERIT FALSE, SET TRUE',rec.runtime_role,rec.login_name);
  IF EXISTS(
   SELECT 1 FROM pg_auth_members m JOIN pg_roles member ON member.oid=m.member
   JOIN pg_roles assigned ON assigned.oid=m.roleid
   WHERE member.rolname=rec.login_name AND assigned.rolname<>rec.runtime_role
  ) THEN
   RAISE EXCEPTION 'identity login has forbidden secondary role membership';
  END IF;
 END LOOP;
END
$principal$;
COMMIT;
