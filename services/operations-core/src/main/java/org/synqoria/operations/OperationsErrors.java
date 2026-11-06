package org.synqoria.operations;

import java.util.Map;
import org.springframework.dao.DataAccessException;
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
   case "stale_version","idempotency_collision"->HttpStatus.CONFLICT;
   case "not_found"->HttpStatus.NOT_FOUND;
   case "workforce_grant_source_not_integrated","database_role_denied"->HttpStatus.SERVICE_UNAVAILABLE;
   default->HttpStatus.FORBIDDEN;
  };
  return ResponseEntity.status(status).body(Map.of("error",code));
 }
 @ExceptionHandler(DataAccessException.class)
 public ResponseEntity<Map<String,String>> databaseUnavailable(DataAccessException ex){
  return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error","operations_unavailable"));
 }
}
