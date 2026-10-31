-- P01-B0004. Draft, NOT executed in PostgreSQL. All writes must occur in the
-- same authoritative transaction as the business state; no unsafe async dual-write.
-- No RLS allow policies until a reviewed, non-spoofable server authorization path exists.
CREATE TABLE clinical_outbox (
  tenant_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  event_id uuid NOT NULL,
  aggregate_type text NOT NULL CHECK (length(btrim(aggregate_type)) BETWEEN 1 AND 80),
  aggregate_id uuid NOT NULL,
  aggregate_revision bigint NOT NULL CHECK (aggregate_revision > 0),
  event_type text NOT NULL CHECK (length(btrim(event_type)) BETWEEN 1 AND 120),
  schema_version integer NOT NULL CHECK (schema_version > 0),
  correlation_id uuid NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
  delivered_at timestamptz,
  delivery_attempts integer NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0),
  PRIMARY KEY (tenant_id, facility_id, event_id),
  FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id),
  UNIQUE (tenant_id, facility_id, aggregate_type, aggregate_id, aggregate_revision, event_type),
  CHECK (delivered_at IS NULL OR delivered_at >= created_at)
);
CREATE INDEX clinical_outbox_pending_idx ON clinical_outbox (tenant_id, facility_id, created_at, event_id)
  WHERE delivered_at IS NULL;
CREATE TABLE clinical_inbox (
  tenant_id uuid NOT NULL,
  facility_id uuid NOT NULL,
  consumer_id text NOT NULL CHECK (length(btrim(consumer_id)) BETWEEN 1 AND 100),
  event_id uuid NOT NULL,
  received_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
  processed_at timestamptz,
  PRIMARY KEY (tenant_id, facility_id, consumer_id, event_id),
  FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id),
  CHECK (processed_at IS NULL OR processed_at >= received_at)
);
ALTER TABLE clinical_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_outbox FORCE ROW LEVEL SECURITY;
ALTER TABLE clinical_inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_inbox FORCE ROW LEVEL SECURITY;
REVOKE ALL ON clinical_outbox, clinical_inbox FROM PUBLIC;
-- Protected PHI payload classification, encryption, retention, privileged DB roles,
-- replay and dispatcher leasing require separate reviewed implementation.
