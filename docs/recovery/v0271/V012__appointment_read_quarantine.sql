-- RECOVERY BRANCH P06-B0018. QUARANTINED: reviewed schema only, NOT applied to PostgreSQL.
-- No table may be queried or written by application identities until trusted admission,
-- non-owner restricted-role RLS testing, capacity/exclusion locking, audit and outbox are proven.
CREATE TABLE scheduling_appointments (
 cell_id uuid NOT NULL,
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 appointment_id uuid NOT NULL,
 patient_id uuid NOT NULL,
 resource_id uuid NOT NULL,
 clinician_id uuid NOT NULL,
 window_start timestamptz NOT NULL,
 window_end timestamptz NOT NULL,
 time_zone text NOT NULL CHECK (length(btrim(time_zone)) BETWEEN 1 AND 80),
 status text NOT NULL CHECK (status IN ('REQUESTED','CONFIRMED','ARRIVED','FULFILLED','CANCELLED','NO_SHOW','PURGED')),
 revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
 PRIMARY KEY (tenant_id, facility_id, appointment_id),
 FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id),
 FOREIGN KEY (tenant_id, patient_id) REFERENCES patient_master_index(tenant_id, patient_id),
 CHECK (window_start < window_end),
 CHECK (window_end <= window_start + interval '12 hours')
);
CREATE INDEX scheduling_appointments_scoped_window ON scheduling_appointments
 (tenant_id, facility_id, resource_id, window_start, window_end) WHERE status IN ('REQUESTED','CONFIRMED','ARRIVED');
-- The following are NOT substitutes for DB exclusion constraints and true transaction locks.
-- No permissive RLS policies, no booking-write endpoint, no customer-visible reservations.
ALTER TABLE scheduling_appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE scheduling_appointments FORCE ROW LEVEL SECURITY;
REVOKE ALL ON scheduling_appointments FROM PUBLIC;
