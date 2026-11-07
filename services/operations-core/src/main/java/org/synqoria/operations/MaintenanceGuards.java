package org.synqoria.operations;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;

/**
 * M23 safety guards called from the existing work-order / asset transitions (inside the scoped, RLS-verified transaction).
 * They only ADD refusals; they never grant anything the state machine did not already allow.
 */
final class MaintenanceGuards {
 private MaintenanceGuards(){}

 /** A technician must be a person with a CURRENT, active, shift-bounded workforce grant that includes m23:maintenance (fail closed). */
 static UUID requireLiveTechnician(JdbcTemplate jdbc,OperationWorkflow.Item workOrder,String technicianRef){
  final UUID technician;
  try{technician=UUID.fromString(technicianRef);}
  catch(IllegalArgumentException | NullPointerException e){throw new WorkflowViolation("technician_must_be_workforce_member");}
  Long live=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_operations.workforce_grants WHERE cell_id=? AND facility_id=? AND actor_id=? "+
   "AND active AND 'm23:maintenance'=ANY(permissions) AND shift_from<=clock_timestamp() "+
   "AND shift_until>clock_timestamp() AND valid_until>clock_timestamp()",
   Long.class,workOrder.cell(),workOrder.facility(),technician);
  if(live==null||live!=1)throw new WorkflowViolation("technician_not_on_verified_shift");
  return technician;
 }

 /** Repair work may only continue while its asset is still offline (MAINTENANCE). */
 static void requireAssetOffline(JdbcTemplate jdbc,OperationWorkflow.Item workOrder){
  Object ref=workOrder.fields().get("assetRef");
  UUID asset;
  try{asset=UUID.fromString(String.valueOf(ref));}
  catch(IllegalArgumentException e){throw new WorkflowViolation("invalid_resource_reference");}
  Long offline=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_operations.items WHERE id=? AND cell_id=? AND facility_id=? AND module_id='M23' "+
   "AND record_type='ASSET' AND status='MAINTENANCE'",Long.class,asset,workOrder.cell(),workOrder.facility());
  if(offline==null||offline!=1)throw new WorkflowViolation("asset_not_offline_for_work");
 }

 /** Independent inspection: the person verifying the work is never the technician who performed it. */
 static void requireIndependentVerifier(JdbcTemplate jdbc,OperationWorkflow.Item verified){
  String technician=jdbc.queryForObject(
   "SELECT technician_ref FROM synqoria_operations.maintenance_orders WHERE cell_id=? AND facility_id=? AND item_id=?",
   String.class,verified.cell(),verified.facility(),verified.id());
  if(technician==null||technician.equalsIgnoreCase(verified.lastActor().toString()))
   throw new WorkflowViolation("verifier_must_be_independent_of_technician");
 }

 /**
  * Fail closed: if the asset's live approved plan requires calibration, the asset cannot return to service unless a calibration
  * record (bound to independent evidence) is currently valid. Assets with no calibration-bearing plan are unaffected.
  */
 static void requireCalibrationCurrent(JdbcTemplate jdbc,OperationWorkflow.Item asset){
  Long required=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_operations.pm_plans p JOIN synqoria_operations.pm_plan_versions v "+
   "ON v.plan_id=p.plan_id AND v.decision='APPROVED' WHERE p.cell_id=? AND p.facility_id=? AND p.asset_ref=? "+
   "AND p.retired_at IS NULL AND v.service_type<>'PREVENTIVE'",Long.class,asset.cell(),asset.facility(),asset.id());
  if(required==null||required==0)return;
  Long current=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_operations.pm_calibrations WHERE cell_id=? AND facility_id=? AND asset_ref=? "+
   "AND next_due_at>clock_timestamp()",Long.class,asset.cell(),asset.facility(),asset.id());
  if(current==null||current<1)throw new WorkflowViolation("calibration_expired_or_missing");
 }
}
