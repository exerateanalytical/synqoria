package org.synqoria.operations;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.operations.domain.ModuleCatalog;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;

@RestController
@RequestMapping("/api/v1/operations")
public final class OperationsApi {
 private final WorkforceAdmission admission;
 private final OperationsStore store;
 public OperationsApi(WorkforceAdmission admission,OperationsStore store){
  this.admission=admission;this.store=store;
 }
 public record NewItem(String recordType,Map<String,Object> fields,UUID commandId){}
 public record TransitionItem(String action,long expectedVersion,Map<String,Object> fields,UUID commandId){}
 @GetMapping("/catalog")
 public Collection<ModuleCatalog.Module> catalog(@AuthenticationPrincipal Jwt jwt){
  admission.requireAuthoritativeGrant(jwt);
  return ModuleCatalog.all().values();
 }
 @GetMapping("/{module}/items")
 public List<OperationWorkflow.Item> list(@PathVariable String module,@RequestParam(defaultValue="50")int limit,
   @AuthenticationPrincipal Jwt jwt){
  return store.list(admission.requireAuthoritativeGrant(jwt),module,limit);
 }
 @GetMapping("/{module}/items/{itemId}")
 public OperationWorkflow.Item one(@PathVariable String module,@PathVariable UUID itemId,
  @AuthenticationPrincipal Jwt jwt){
  return store.one(admission.requireAuthoritativeGrant(jwt),module,itemId);
 }
 @PostMapping("/{module}/items")
 @ResponseStatus(HttpStatus.CREATED)
 public OperationWorkflow.Item create(@PathVariable String module,@RequestBody NewItem request,
  @AuthenticationPrincipal Jwt jwt){
  if(request==null||request.commandId()==null)throw new WorkflowViolation("idempotency_key_required");
  return store.create(admission.requireAuthoritativeGrant(jwt),module,request);
 }
 @PostMapping("/{module}/items/{itemId}/transitions")
 public OperationWorkflow.Item transition(@PathVariable String module,@PathVariable UUID itemId,
  @RequestBody TransitionItem request,@AuthenticationPrincipal Jwt jwt){
  if(request==null||request.commandId()==null||request.expectedVersion()<1)
   throw new WorkflowViolation("invalid_transition");
  return store.transition(admission.requireAuthoritativeGrant(jwt),module,itemId,request);
 }
 @GetMapping("/M22/items/{itemId}/balances")
 public List<OperationsStore.StockBalanceView> balances(@PathVariable UUID itemId,
   @AuthenticationPrincipal Jwt jwt){
  return store.balances(admission.requireAuthoritativeGrant(jwt),itemId);
 }
 @PostMapping("/M22/items/{itemId}/movements")
 public OperationsStore.StockBalanceView moveStock(@PathVariable UUID itemId,
   @RequestBody OperationsStore.StockMovementRequest request,
   @AuthenticationPrincipal Jwt jwt){
  return store.moveStock(admission.requireAuthoritativeGrant(jwt),itemId,request);
 }
}
