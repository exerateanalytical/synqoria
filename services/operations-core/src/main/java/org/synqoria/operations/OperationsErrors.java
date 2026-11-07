package org.synqoria.operations;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.synqoria.operations.domain.WorkflowViolation;

@RestControllerAdvice
public class OperationsErrors {
 @ExceptionHandler(WorkflowViolation.class)
 public ResponseEntity<Map<String,String>> denied(WorkflowViolation ex){
  String code=ex.getMessage();
  HttpStatus status=switch(code){
   case "stale_version","idempotency_collision","idempotency_result_superseded",
        "planner_lease_held","planner_lease_lost","asset_already_under_service","plan_exists_use_new_version",
        "policy_decision_pending","plan_decision_pending","decision_already_made","evidence_already_used",
        "reference_already_recorded","cost_already_recorded"->HttpStatus.CONFLICT;
   case "invalid_mortuary_action","invalid_mortuary_reference",
        "missing_required_identifier","invalid_mortuary_intake","invalid_limit","invalid_range","invalid_horizon",
        "invalid_interval","invalid_calibration_interval","invalid_schedule","invalid_timezone","invalid_service_type",
        "invalid_calendar_rule","invalid_calibration_time","invalid_amount","invalid_currency","invalid_reference_kind",
        "invalid_issuer","invalid_threshold","invalid_grace","invalid_escalation","idempotency_key_required"->HttpStatus.BAD_REQUEST;
   case "not_found"->HttpStatus.NOT_FOUND;
   case "workforce_grant_source_not_integrated","database_role_denied"->HttpStatus.SERVICE_UNAVAILABLE;
   default->HttpStatus.FORBIDDEN;
  };
  return ResponseEntity.status(status).body(Map.of("error",code));
 }
 @ExceptionHandler(DataIntegrityViolationException.class)
 public ResponseEntity<Map<String,String>> conflict(DataIntegrityViolationException ex){
  // Never expose IDs, death records or underlying database constraint statements.
  return ResponseEntity.status(HttpStatus.CONFLICT)
    .body(Map.of("error","mortuary_or_resource_integrity_conflict"));
 }
 @ExceptionHandler(DataAccessException.class)
 public ResponseEntity<Map<String,String>> databaseUnavailable(DataAccessException ex){
  return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error","operations_unavailable"));
 }
}
