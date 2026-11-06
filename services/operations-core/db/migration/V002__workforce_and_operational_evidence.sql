-- SYNQORIA V002 — private authority-fed workforce roster and signed operational evidence.
-- A distinct migration/identity issuer account owns these tables. Runtime is read-only.
-- Apply after V001 in a DISPOSABLE hospital-cell database first. Never seed fabricated staff.
BEGIN;
CREATE TABLE synqoria_operations.workforce_grants(
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 grant_revision bigint NOT NULL CHECK(grant_revision>=1),
 permissions text[] NOT NULL,
 active boolean NOT NULL DEFAULT false,
 shift_from timestamptz NOT NULL,
 shift_until timestamptz NOT NULL,
 valid_until timestamptz NOT NULL,
 approved_by uuid NOT NULL,
 source_ref varchar(160) NOT NULL,
 reviewed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(cell_id,facility_id,actor_id),
 CHECK(shift_until>shift_from AND valid_until>shift_from),
 CHECK(array_length(permissions,1) BETWEEN 1 AND 128),
 CHECK(approved_by<>actor_id),
 CHECK(source_ref <> '')
);
CREATE TABLE synqoria_operations.external_evidence(
 evidence_id uuid PRIMARY KEY,
 cell_id uuid NOT NULL,
 facility_id uuid NOT NULL,
 item_id uuid NOT NULL,
 evidence_kind varchar(64) NOT NULL CHECK(evidence_kind IN
  ('supplier_qualified','purchase_receipt','invoice_matched','stock_qc','stock_receipt',
   'stock_return','stock_adjustment','biomed_inspection','calibration_safe',
   'vehicle_driver_qualified','transport_handover','impact_approved',
   'verified_signature','document_lineage','delivery_receipt','delivery_failure')),
 external_ref varchar(160) NOT NULL,
 issuer_ref varchar(160) NOT NULL,
 approver_id uuid NOT NULL,
 valid_from timestamptz NOT NULL,
 valid_until timestamptz NOT NULL,
 revoked_at timestamptz,
 digest_sha256 char(64) NOT NULL CHECK(digest_sha256 ~ '^[0-9a-f]{64}$'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(valid_until>valid_from),
 CHECK(external_ref <> '' AND issuer_ref <> ''),
 UNIQUE(cell_id,facility_id,item_id,evidence_kind,external_ref),
 FOREIGN KEY(cell_id,facility_id,item_id)
   REFERENCES synqoria_operations.items(cell_id,facility_id,id)
);
CREATE INDEX evidence_lookup_idx ON synqoria_operations.external_evidence
 (cell_id,facility_id,item_id,evidence_kind,valid_until DESC)
 WHERE revoked_at IS NULL;
-- Do not permit runtime to grant itself identity or evidence.
REVOKE ALL ON synqoria_operations.workforce_grants FROM PUBLIC;
REVOKE ALL ON synqoria_operations.external_evidence FROM PUBLIC;
GRANT SELECT ON synqoria_operations.workforce_grants TO synqoria_operations_runtime;
GRANT SELECT ON synqoria_operations.external_evidence TO synqoria_operations_runtime;
DO $policy$
DECLARE tab text;
BEGIN
 FOREACH tab IN ARRAY ARRAY['workforce_grants','external_evidence']
 LOOP
  EXECUTE format('ALTER TABLE synqoria_operations.%I ENABLE ROW LEVEL SECURITY',tab);
  EXECUTE format('ALTER TABLE synqoria_operations.%I FORCE ROW LEVEL SECURITY',tab);
  EXECUTE format(
   'CREATE POLICY operations_scoped_authority ON synqoria_operations.%I ' ||
   'FOR SELECT TO synqoria_operations_runtime ' ||
   'USING(cell_id=NULLIF(current_setting(''synqoria.cell'',true),'''')::uuid ' ||
   'AND facility_id=NULLIF(current_setting(''synqoria.facility'',true),'''')::uuid)',tab);
 END LOOP;
END $policy$;
COMMIT;
