package org.synqoria.operations.mortuary;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.operations.WorkforceAdmission;
import org.synqoria.operations.domain.WorkflowViolation;
import static org.synqoria.operations.mortuary.MortuaryModels.*;

@RestController
@RequestMapping("/api/v1/operations/M27")
public final class MortuaryApi {
 private final WorkforceAdmission admission;
 private final MortuaryStore store;
 public MortuaryApi(WorkforceAdmission admission,MortuaryStore store){
  this.admission=admission;this.store=store;
 }
 @GetMapping("/cases")
 public List<CaseView> list(@RequestParam(defaultValue="50")int limit,
                           @AuthenticationPrincipal Jwt jwt){
  return store.list(admission.requireAuthoritativeGrant(jwt),limit);
 }
 @GetMapping("/cases/{id}")
 public CaseView one(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
  return store.one(admission.requireAuthoritativeGrant(jwt),id);
 }
 @PostMapping("/cases")
 @ResponseStatus(HttpStatus.CREATED)
 public CaseView receive(@RequestBody Intake request,@AuthenticationPrincipal Jwt jwt){
  if(request==null||request.commandId()==null)
   throw new WorkflowViolation("idempotency_key_required");
  return store.receive(admission.requireAuthoritativeGrant(jwt),request);
 }
 @PostMapping("/cases/{id}/actions")
 public CaseView action(@PathVariable UUID id,@RequestBody Change request,
                         @AuthenticationPrincipal Jwt jwt){
  if(request==null||request.commandId()==null||request.expectedVersion()<1)
   throw new WorkflowViolation("invalid_mortuary_action");
  return store.change(admission.requireAuthoritativeGrant(jwt),id,request);
 }
 @GetMapping("/cases/{id}/custody")
 public List<CustodyView> custody(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
  return store.custodyHistory(admission.requireAuthoritativeGrant(jwt),id);
 }
 @GetMapping("/cases/{id}/holds")
 public List<HoldView> holds(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
  return store.legalHolds(admission.requireAuthoritativeGrant(jwt),id);
 }
 @GetMapping("/cases/{id}/approvals")
 public List<ApprovalView> approvals(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
  return store.approvals(admission.requireAuthoritativeGrant(jwt),id);
 }
 @GetMapping("/cases/{id}/audit")
 public List<AuditView> audit(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
  return store.audit(admission.requireAuthoritativeGrant(jwt),id);
 }
}
