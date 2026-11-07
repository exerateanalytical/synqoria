package org.synqoria.operations;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.operations.domain.WorkflowViolation;

/**
 * M23 preventive-maintenance API. Every call is admitted through the SAME verified-JWT + live workforce-grant gate as the rest of
 * operations; the literal /M23/maintenance prefix is more specific than the generic /{module}/items routes, so no existing route changes.
 */
@RestController
@RequestMapping("/api/v1/operations/M23/maintenance")
public final class MaintenanceApi {
 private final WorkforceAdmission admission;
 private final MaintenanceService service;
 public MaintenanceApi(WorkforceAdmission admission,MaintenanceService service){this.admission=admission;this.service=service;}
 public record GenerateRequest(Integer horizonDays,UUID commandId){}
 public record CommandOnly(UUID commandId){}
 private static Instant instant(String value){
  try{return Instant.parse(value);}catch(RuntimeException e){throw new WorkflowViolation("invalid_range");}
 }

 @GetMapping("/policies")
 public List<Map<String,Object>> policies(@AuthenticationPrincipal Jwt jwt){return service.policies(admission.requireAuthoritativeGrant(jwt));}
 @PostMapping("/policies") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> proposePolicy(@RequestBody MaintenanceService.PolicyRequest r,@AuthenticationPrincipal Jwt jwt){
  return service.proposePolicy(admission.requireAuthoritativeGrant(jwt),r);
 }
 @PostMapping("/policies/{safetyClass}/versions/{version}/decision")
 public Map<String,Object> decidePolicy(@PathVariable String safetyClass,@PathVariable int version,@RequestBody MaintenanceService.Decision d,@AuthenticationPrincipal Jwt jwt){
  return service.decidePolicy(admission.requireAuthoritativeGrant(jwt),safetyClass,version,d);
 }

 @GetMapping("/plans")
 public List<Map<String,Object>> plans(@RequestParam(defaultValue="50") int limit,@AuthenticationPrincipal Jwt jwt){return service.plans(admission.requireAuthoritativeGrant(jwt),limit);}
 @PostMapping("/plans") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> proposePlan(@RequestBody MaintenanceService.PlanRequest r,@AuthenticationPrincipal Jwt jwt){
  return service.proposePlan(admission.requireAuthoritativeGrant(jwt),r);
 }
 @PostMapping("/plans/{planId}/versions") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> proposeVersion(@PathVariable UUID planId,@RequestBody MaintenanceService.PlanRequest r,@AuthenticationPrincipal Jwt jwt){
  return service.proposeVersion(admission.requireAuthoritativeGrant(jwt),planId,r);
 }
 @PostMapping("/plans/{planId}/versions/{version}/decision")
 public Map<String,Object> decidePlan(@PathVariable UUID planId,@PathVariable int version,@RequestBody MaintenanceService.Decision d,@AuthenticationPrincipal Jwt jwt){
  return service.decidePlan(admission.requireAuthoritativeGrant(jwt),planId,version,d);
 }

 @PostMapping("/planner/generate")
 public Map<String,Object> generate(@RequestBody GenerateRequest r,@AuthenticationPrincipal Jwt jwt){
  int horizon=r.horizonDays()==null?30:r.horizonDays();
  return service.runPlanner(admission.requireAuthoritativeGrant(jwt),r.commandId(),horizon);
 }
 @PostMapping("/planner/escalate")
 public Map<String,Object> escalate(@RequestBody CommandOnly r,@AuthenticationPrincipal Jwt jwt){
  return service.escalateOverdue(admission.requireAuthoritativeGrant(jwt),r.commandId());
 }
 @GetMapping("/planner/health")
 public Map<String,Object> health(@RequestParam(defaultValue="120") int staleAfterMinutes,@AuthenticationPrincipal Jwt jwt){
  return service.health(admission.requireAuthoritativeGrant(jwt),staleAfterMinutes);
 }

 @GetMapping("/calendar")
 public List<Map<String,Object>> calendar(@RequestParam String from,@RequestParam String to,@RequestParam(defaultValue="100") int limit,@AuthenticationPrincipal Jwt jwt){
  return service.calendar(admission.requireAuthoritativeGrant(jwt),instant(from),instant(to),limit);
 }
 @PostMapping("/occurrences/{occurrenceId}/work-order")
 public Map<String,Object> link(@PathVariable UUID occurrenceId,@RequestBody MaintenanceService.LinkRequest r,@AuthenticationPrincipal Jwt jwt){
  return service.linkWorkOrder(admission.requireAuthoritativeGrant(jwt),occurrenceId,r);
 }
 @PostMapping("/occurrences/{occurrenceId}/completion")
 public Map<String,Object> complete(@PathVariable UUID occurrenceId,@RequestBody MaintenanceService.CompleteRequest r,@AuthenticationPrincipal Jwt jwt){
  return service.completeOccurrence(admission.requireAuthoritativeGrant(jwt),occurrenceId,r);
 }

 @GetMapping("/assets/{assetId}/history")
 public Map<String,Object> history(@PathVariable UUID assetId,@RequestParam(defaultValue="50") int limit,@AuthenticationPrincipal Jwt jwt){
  return service.history(admission.requireAuthoritativeGrant(jwt),assetId,limit);
 }
 @PostMapping("/assets/{assetId}/calibrations") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> calibrate(@PathVariable UUID assetId,@RequestBody MaintenanceService.CalibrationRequest r,@AuthenticationPrincipal Jwt jwt){
  return service.recordCalibration(admission.requireAuthoritativeGrant(jwt),assetId,r);
 }
 @GetMapping("/inspections/pending")
 public List<Map<String,Object>> pending(@RequestParam(defaultValue="50") int limit,@AuthenticationPrincipal Jwt jwt){
  return service.inspectionQueue(admission.requireAuthoritativeGrant(jwt),limit);
 }
 @PostMapping("/work-orders/{workOrderId}/service-references") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> serviceReference(@PathVariable UUID workOrderId,@RequestBody MaintenanceService.ServiceReferenceRequest r,@AuthenticationPrincipal Jwt jwt){
  return service.addServiceReference(admission.requireAuthoritativeGrant(jwt),workOrderId,r);
 }
 @PostMapping("/work-orders/{workOrderId}/costs") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> cost(@PathVariable UUID workOrderId,@RequestBody MaintenanceService.CostRequest r,@AuthenticationPrincipal Jwt jwt){
  return service.addCostReference(admission.requireAuthoritativeGrant(jwt),workOrderId,r);
 }
}
