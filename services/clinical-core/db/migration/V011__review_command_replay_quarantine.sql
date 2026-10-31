-- P05-B0059: UNAPPLIED. DBA must review existing duplicate commands before creating index.
-- A command may never produce multiple review events in one tenant/facility.
CREATE UNIQUE INDEX patient_review_event_command_once_idx
 ON patient_registration_review_events(tenant_id,facility_id,command_id);
-- The existing FORCE RLS and no-grants policy remains unchanged.
REVOKE ALL ON patient_registration_review_events FROM PUBLIC;
