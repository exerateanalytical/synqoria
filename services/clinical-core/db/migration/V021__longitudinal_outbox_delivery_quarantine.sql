-- SYNQORIA P07 quarantined outbox delivery state; intentionally NO runtime grants/policies.
-- No PHI payloads or clinical notes in the delivery table. Fencing makes retries at-least-once.
CREATE TABLE synqoria_longitudinal_delivery_quarantine (
 tenant_id uuid NOT NULL, facility_id uuid NOT NULL, patient_id uuid NOT NULL, encounter_id uuid NOT NULL,
 command_id uuid NOT NULL, revision bigint NOT NULL CHECK (revision BETWEEN 1 AND 20000),
 event_hash char(64) NOT NULL CHECK (event_hash ~ '^[0-9a-f]{64}$'),
 event_type varchar(96) NOT NULL CHECK (event_type ~ '^[a-z][a-z0-9_.-]{0,95}$'),
 attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 12),
 lease_token uuid, lease_expires_at timestamptz,
 published_at timestamptz, parked_at timestamptz,
 CHECK (lease_token IS NULL OR (lease_expires_at IS NOT NULL AND published_at IS NULL AND parked_at IS NULL)),
 CHECK (published_at IS NULL OR (lease_token IS NULL AND parked_at IS NULL)),
 CHECK (parked_at IS NULL OR (lease_token IS NULL AND published_at IS NULL)),
 PRIMARY KEY (tenant_id,facility_id,patient_id,encounter_id,command_id),
 UNIQUE (tenant_id,facility_id,patient_id,encounter_id,revision),
 FOREIGN KEY (tenant_id,facility_id,patient_id,encounter_id,command_id)
  REFERENCES synqoria_longitudinal_outbox_quarantine (tenant_id,facility_id,patient_id,encounter_id,command_id)
);
CREATE INDEX synqoria_longitudinal_delivery_ready_quarantine
 ON synqoria_longitudinal_delivery_quarantine
 (tenant_id, facility_id, patient_id, encounter_id, revision)
 WHERE published_at IS NULL AND parked_at IS NULL;
ALTER TABLE synqoria_longitudinal_delivery_quarantine ENABLE ROW LEVEL SECURITY;
ALTER TABLE synqoria_longitudinal_delivery_quarantine FORCE ROW LEVEL SECURITY;
REVOKE ALL ON synqoria_longitudinal_delivery_quarantine FROM PUBLIC;
-- A later separately reviewed migration must bind worker role, scope policies, restricted grants and broker config.
