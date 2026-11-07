package org.synqoria.operations;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.operations.domain.OperationWorkflow;

/**
 * Exact fixed M21/M22/M25/M29/M37 integration routes.
 * Requires real P03 OIDC plus independent current workforce grant verification.
 * No generic admin SQL, grant bypass, or self-certified supplier/delivery evidence.
 */
@RestController
@RequestMapping("/api/v1/operations/integrations")
public final class OperationsIntegrationApi {
 private final WorkforceAdmission admission;
 private final OperationsIntegrationService extensions;
 public OperationsIntegrationApi(WorkforceAdmission admission,OperationsIntegrationService extensions){
  this.admission=admission;this.extensions=extensions;
 }
 private OperationWorkflow.Actor actor(Jwt jwt){return admission.requireAuthoritativeGrant(jwt);}
 @GetMapping("/M21/procurement-reconciliation")
 public List<OperationsIntegrationService.ProcurementStatus> procurement(
  @RequestParam(defaultValue="50")int limit,@AuthenticationPrincipal Jwt jwt){
  return extensions.procurementStatus(actor(jwt),limit);
 }
 @PostMapping("/M22/stock-counts/{itemId}")
 public OperationsIntegrationService.StockCount count(
  @PathVariable UUID itemId,@RequestBody OperationsIntegrationService.CountRequest request,
  @AuthenticationPrincipal Jwt jwt){return extensions.captureCount(actor(jwt),itemId,request);}
 public record Review(boolean accept){}
 @PostMapping("/M22/stock-counts/{countId}/review")
 public OperationsIntegrationService.StockCount review(
  @PathVariable UUID countId,@RequestBody Review request,@AuthenticationPrincipal Jwt jwt){
  if(request==null)throw new org.synqoria.operations.domain.WorkflowViolation("invalid_stock_count");
  return extensions.reviewCount(actor(jwt),countId,request.accept());
 }
 @GetMapping("/M25/available-vehicles")
 public List<Map<String,Object>> vehicles(@RequestParam(defaultValue="50")int limit,
  @AuthenticationPrincipal Jwt jwt){return extensions.availableVehicles(actor(jwt),limit);}
 @GetMapping("/M29/publication-feed")
 public List<Map<String,Object>> changes(@RequestParam(required=false)String after,
  @RequestParam(defaultValue="50")int limit,@AuthenticationPrincipal Jwt jwt){
  return extensions.publishedChanges(actor(jwt),after,limit);
 }
 @GetMapping("/M37/due-deliveries")
 public List<Map<String,Object>> due(@RequestParam(defaultValue="50")int limit,
  @AuthenticationPrincipal Jwt jwt){return extensions.dueNotifications(actor(jwt),limit);}
}
