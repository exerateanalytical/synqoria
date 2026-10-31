-- P05-B0042. UNAPPLIED restricted-review event integrity contract.
-- This draft requires DBA/clinical security approval and a backfill plan for any existing rows.
-- Do not deploy or grant access until a restricted role and RLS tests are independently executed.
ALTER TABLE patient_registration_review_events
 ADD COLUMN previous_digest_sha256 char(64),
 ADD COLUMN event_digest_sha256 char(64);
ALTER TABLE patient_registration_review_events
 ADD CONSTRAINT review_previous_digest_format CHECK(previous_digest_sha256 ~ '^[0-9a-f]{64}$'),
 ADD CONSTRAINT review_event_digest_format CHECK(event_digest_sha256 ~ '^[0-9a-f]{64}$');
-- NOT NULL is deliberately deferred until existing rows (if any) are audited/backfilled.
-- No RLS policies, no grants, and no clinical release from this migration.
REVOKE ALL ON patient_registration_review_events FROM PUBLIC;
