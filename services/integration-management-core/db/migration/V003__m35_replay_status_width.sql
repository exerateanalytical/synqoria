-- M35 approved-but-not-dispatched status must never be silently truncated.
BEGIN;
ALTER TABLE synqoria_integration.replay_requests
 ALTER COLUMN status TYPE varchar(32);
COMMIT;