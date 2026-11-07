package org.synqoria.content;

import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.content.Db.Reply;
import org.synqoria.content.domain.Domain.*;

/** Versioned API /api/v1/content. Contract: contracts/openapi/synqoria-m30-content-v1.yaml. */
@RestController
@RequestMapping("/api/v1/content")
public class Api {
 private final Clock clock;
 private final TerminologyService terms;
 private final FormularyService formulary;
 private final PackageService packages;
 private final ActivationService activations;

 public Api(Clock clock,TerminologyService terms,FormularyService formulary,PackageService packages,ActivationService activations){
  this.clock=clock;this.terms=terms;this.formulary=formulary;this.packages=packages;this.activations=activations;
 }
 private Actor actor(Jwt jwt){return Platform.actorFrom(jwt,clock.instant());}
 private static UUID key(String h){
  if(h==null)throw new Violation("idempotency_key_required");
  try{return UUID.fromString(h);}catch(IllegalArgumentException e){throw new Violation("idempotency_key_required");}
 }
 private static ResponseEntity<Object> out(Reply r){return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());}
 private static <T> ResponseEntity<T> ok(T b){return ResponseEntity.ok().header("Cache-Control","no-store").body(b);}
 private static <T> T req(T b){if(b==null)throw new Violation("malformed_request");return b;}
 private static final String K="Idempotency-Key";

 // ---------------------------------------------------------------- M30-S01 terminology
 @GetMapping("/code-systems")
 public ResponseEntity<Map<String,Object>> systems(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){
  return ok(terms.systems(actor(j),page,size));
 }
 @PostMapping("/code-systems")
 public ResponseEntity<Object> createSystem(@RequestHeader(value=K,required=false)String k,@RequestBody TerminologyService.SystemRequest b,@AuthenticationPrincipal Jwt j){
  return out(terms.createSystem(actor(j),key(k),req(b)));
 }
 @GetMapping("/code-systems/{id}/versions")
 public ResponseEntity<List<Map<String,Object>>> versions(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(terms.versions(actor(j),id));}
 @PostMapping("/code-systems/{id}/imports")
 public ResponseEntity<Object> importVersion(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TerminologyService.ImportRequest b,@AuthenticationPrincipal Jwt j){
  return out(terms.importVersion(actor(j),key(k),id,req(b)));
 }
 @GetMapping("/concepts")
 public ResponseEntity<Map<String,Object>> search(@RequestParam(required=false)UUID systemId,@RequestParam(required=false)UUID versionId,@RequestParam(required=false)String q,
   @RequestParam(required=false)String status,@RequestParam(defaultValue="false")boolean includeInactive,@RequestParam(defaultValue="0")int page,
   @RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){
  return ok(terms.search(actor(j),systemId,versionId,q,status,includeInactive,page,size));
 }
 @GetMapping("/code-system-versions/{versionId}/roots")
 public ResponseEntity<Map<String,Object>> roots(@PathVariable UUID versionId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@AuthenticationPrincipal Jwt j){
  return ok(terms.roots(actor(j),versionId,page,size));
 }
 @GetMapping("/code-system-versions/{versionId}/concepts/{code}")
 public ResponseEntity<Map<String,Object>> concept(@PathVariable UUID versionId,@PathVariable String code,@AuthenticationPrincipal Jwt j){
  return ok(terms.concept(actor(j),versionId,code));
 }
 @GetMapping("/mappings")
 public ResponseEntity<Map<String,Object>> mappings(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){
  return ok(terms.listMappings(actor(j),status,page,size));
 }
 @PostMapping("/mappings")
 public ResponseEntity<Object> propose(@RequestHeader(value=K,required=false)String k,@RequestBody TerminologyService.MappingRequest b,@AuthenticationPrincipal Jwt j){
  return out(terms.propose(actor(j),key(k),req(b)));
 }
 @PostMapping("/mappings/{id}/decision")
 public ResponseEntity<Object> decideMapping(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TerminologyService.MappingDecision b,@AuthenticationPrincipal Jwt j){
  return out(terms.decide(actor(j),key(k),id,req(b)));
 }

 // ---------------------------------------------------------------- M30-S02 formulary
 @GetMapping("/formulary")
 public ResponseEntity<Map<String,Object>> formularies(@RequestParam(required=false)String status,@RequestParam(required=false)String q,@RequestParam(defaultValue="0")int page,
   @RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(formulary.list(actor(j),status,q,page,size));}
 @PostMapping("/formulary")
 public ResponseEntity<Object> createEntry(@RequestHeader(value=K,required=false)String k,@RequestBody FormularyService.EntryRequest b,@AuthenticationPrincipal Jwt j){
  return out(formulary.create(actor(j),key(k),req(b)));
 }
 @GetMapping("/formulary/{id}")
 public ResponseEntity<Map<String,Object>> entry(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(formulary.get(actor(j),id));}
 @PostMapping("/formulary/{id}/update")
 public ResponseEntity<Object> updateEntry(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FormularyService.UpdateRequest b,@AuthenticationPrincipal Jwt j){
  return out(formulary.update(actor(j),key(k),id,req(b)));
 }
 @PostMapping("/formulary/{id}/submit")
 public ResponseEntity<Object> submitEntry(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FormularyService.Transition b,@AuthenticationPrincipal Jwt j){
  return out(formulary.submit(actor(j),key(k),id,req(b)));
 }
 @PostMapping("/formulary/{id}/withdraw")
 public ResponseEntity<Object> withdrawEntry(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FormularyService.Transition b,@AuthenticationPrincipal Jwt j){
  return out(formulary.withdraw(actor(j),key(k),id,req(b)));
 }
 @PostMapping("/formulary/{id}/decision")
 public ResponseEntity<Object> decideEntry(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FormularyService.Decision b,@AuthenticationPrincipal Jwt j){
  return out(formulary.decide(actor(j),key(k),id,req(b)));
 }

 // ---------------------------------------------------------------- M30-S03 / S04 packages and approval
 @GetMapping("/packages")
 public ResponseEntity<Map<String,Object>> packageList(@RequestParam(required=false)String status,@RequestParam(required=false)String key,@RequestParam(defaultValue="0")int page,
   @RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(packages.list(actor(j),status,key,page,size));}
 @PostMapping("/packages")
 public ResponseEntity<Object> createPackage(@RequestHeader(value=K,required=false)String k,@RequestBody PackageService.PackageRequest b,@AuthenticationPrincipal Jwt j){
  return out(packages.create(actor(j),key(k),req(b)));
 }
 @GetMapping("/packages/{id}")
 public ResponseEntity<Map<String,Object>> packageDetail(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(packages.get(actor(j),id));}
 @PostMapping("/packages/{id}/validate")
 public ResponseEntity<Object> validatePackage(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PackageService.Transition b,@AuthenticationPrincipal Jwt j){
  return out(packages.validate(actor(j),key(k),id,req(b)));
 }
 @PostMapping("/packages/{id}/submit")
 public ResponseEntity<Object> submitPackage(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PackageService.Transition b,@AuthenticationPrincipal Jwt j){
  return out(packages.submit(actor(j),key(k),id,req(b)));
 }
 @PostMapping("/packages/{id}/review")
 public ResponseEntity<Object> reviewPackage(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PackageService.ReviewRequest b,@AuthenticationPrincipal Jwt j){
  return out(packages.review(actor(j),key(k),id,req(b)));
 }
 @PostMapping("/packages/{id}/evaluate")
 public ResponseEntity<Map<String,Object>> evaluate(@PathVariable UUID id,@RequestBody PackageService.EvaluateRequest b,@AuthenticationPrincipal Jwt j){
  return ok(packages.evaluate(actor(j),id,req(b)));
 }
 @PostMapping("/approvals/{id}/revoke")
 public ResponseEntity<Object> revokeApproval(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PackageService.RevokeRequest b,@AuthenticationPrincipal Jwt j){
  return out(packages.revoke(actor(j),key(k),id,req(b)));
 }

 // ---------------------------------------------------------------- M30-S05 activation
 @GetMapping("/activations/active")
 public ResponseEntity<Map<String,Object>> active(@AuthenticationPrincipal Jwt j){return ok(activations.active(actor(j)));}
 @GetMapping("/activations")
 public ResponseEntity<Map<String,Object>> history(@RequestParam(required=false)String subjectKey,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){
  return ok(activations.history(actor(j),subjectKey,page,size));
 }
 @GetMapping("/activations/impact")
 public ResponseEntity<Map<String,Object>> impact(@RequestParam String subjectType,@RequestParam UUID subjectId,@AuthenticationPrincipal Jwt j){
  return ok(activations.impact(actor(j),subjectType,subjectId));
 }
 @PostMapping("/activations")
 public ResponseEntity<Object> activate(@RequestHeader(value=K,required=false)String k,@RequestBody ActivationService.ActivateRequest b,@AuthenticationPrincipal Jwt j){
  return out(activations.activate(actor(j),key(k),req(b)));
 }
 @PostMapping("/activations/{id}/rollback")
 public ResponseEntity<Object> rollback(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ActivationService.RollbackRequest b,@AuthenticationPrincipal Jwt j){
  return out(activations.rollback(actor(j),key(k),id,req(b)));
 }
}
