-- P01-B0028. DRAFT ONLY. Run with restricted non-owner DB roles after review.
-- No client/table-owner bypass and no RLS grant is defined in this revision.
ALTER TABLE patient_master_index ADD CONSTRAINT patient_home_facility_unique UNIQUE (tenant_id, patient_id, home_facility_id);
CREATE TABLE patient_registration_demographics (
 tenant_id uuid NOT NULL,
 patient_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 legal_name text,
 date_of_birth date,
 estimated_age_years integer,
 birth_information_status text NOT NULL CHECK (birth_information_status IN ('KNOWN','ESTIMATED_AGE','UNKNOWN')),
 preferred_language text CHECK (preferred_language IN ('en','fr')),
 recorded_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id, patient_id),
 FOREIGN KEY (tenant_id, patient_id, facility_id) REFERENCES patient_master_index(tenant_id, patient_id, home_facility_id),
 FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id),
 CHECK (legal_name IS NULL OR length(btrim(legal_name)) BETWEEN 1 AND 200),
 CHECK (estimated_age_years IS NULL OR estimated_age_years BETWEEN 0 AND 130),
 CHECK ((birth_information_status = 'KNOWN' AND date_of_birth IS NOT NULL AND estimated_age_years IS NULL)
    OR (birth_information_status = 'ESTIMATED_AGE' AND date_of_birth IS NULL AND estimated_age_years IS NOT NULL)
    OR (birth_information_status = 'UNKNOWN' AND date_of_birth IS NULL AND estimated_age_years IS NULL))
);
CREATE TABLE patient_registration_audit (
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 event_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 patient_id uuid NOT NULL,
 correlation_id uuid NOT NULL,
 recorded_at timestamptz NOT NULL,
 operation text NOT NULL CHECK (operation = 'patient:register:provisional'),
 PRIMARY KEY (tenant_id, facility_id, event_id),
 FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id),
 FOREIGN KEY (tenant_id, patient_id, facility_id) REFERENCES patient_master_index(tenant_id, patient_id, home_facility_id)
);
CREATE TABLE patient_registration_command_claim (
 tenant_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 command_id uuid NOT NULL,
 patient_id uuid NOT NULL,
 fingerprint_hmac bytea NOT NULL CHECK (octet_length(fingerprint_hmac) = 32),
 PRIMARY KEY (tenant_id, facility_id, command_id),
 FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id),
 FOREIGN KEY (tenant_id, patient_id, facility_id) REFERENCES patient_master_index(tenant_id, patient_id, home_facility_id)
);
ALTER TABLE patient_registration_demographics ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_registration_demographics FORCE ROW LEVEL SECURITY;
ALTER TABLE patient_registration_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_registration_audit FORCE ROW LEVEL SECURITY;
ALTER TABLE patient_registration_command_claim ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_registration_command_claim FORCE ROW LEVEL SECURITY;
REVOKE ALL ON patient_registration_demographics, patient_registration_audit, patient_registration_command_claim FROM PUBLIC;
-- Append-only audit custody, policy-controlled grants, and named roles are release blockers.
-- Fingerprint must be HMAC-SHA256 from server-held key, never hash raw demographics.
