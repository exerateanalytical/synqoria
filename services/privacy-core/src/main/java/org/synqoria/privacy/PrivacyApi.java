package org.synqoria.privacy;

import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static org.synqoria.privacy.Rules.*;

@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyApi {
 private final PrivacyStore store;private final Clock clock;
 public PrivacyApi(PrivacyStore store,Clock clock){this.store=store;this.clock=clock;}
 private Actor actor(Jwt jwt){return PrivacyPlatform.actor(jwt,clock.instant());}
 private static UUID key(String header){
  try{return UUID.fromString(Objects.requireNonNull(header));}
  catch(IllegalArgumentException|NullPointerException e){throw new Problem("idempotency_key_required");}
 }
 private static ResponseEntity<Object> reply(PrivacyStore.Reply r){
  return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());
 }
 private static <T> ResponseEntity<T> ok(T body){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(body);
 }
 @GetMapping("/directives")
 public ResponseEntity<List<Map<String,Object>>> directives(@AuthenticationPrincipal Jwt jwt,
       @RequestParam(defaultValue="25")int limit){
  return ok(store.listing(actor(jwt),"directives",limit));
 }
 @PostMapping("/directives")
 public ResponseEntity<Object> newDirective(@AuthenticationPrincipal Jwt jwt,
       @RequestHeader(value="Idempotency-Key",required=false)String key,
       @RequestBody Directive request){
  return reply(store.create(actor(jwt),key(key),request));
 }
 @PostMapping("/directives/{id}/verify")
 public ResponseEntity<Object> verify(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
       @RequestHeader(value="Idempotency-Key",required=false)String key,
       @RequestBody Verification req){
  return reply(store.verify(actor(jwt),key(key),id,req));
 }
 @PostMapping("/directives/{id}/activate")
 public ResponseEntity<Object> activate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
       @RequestHeader(value="Idempotency-Key",required=false)String key,
       @RequestBody Version req){
  return reply(store.activate(actor(jwt),key(key),id,req));
 }
 @PostMapping("/directives/{id}/revoke")
 public ResponseEntity<Object> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
       @RequestHeader(value="Idempotency-Key",required=false)String key,
       @RequestBody Version req){
  return reply(store.revoke(actor(jwt),key(key),id,req));
 }
 @GetMapping("/authorization/evaluate")
 public ResponseEntity<Map<String,Object>> evaluate(@AuthenticationPrincipal Jwt jwt,
       @RequestParam UUID subject,@RequestParam String purpose,
       @RequestParam String recipient,@RequestParam String operation){
  return ok(store.evaluate(actor(jwt),subject,purpose,recipient,operation));
 }
 @GetMapping("/disclosures")
 public ResponseEntity<List<Map<String,Object>>> disclosures(@AuthenticationPrincipal Jwt jwt,
       @RequestParam(defaultValue="25")int limit){
  return ok(store.listing(actor(jwt),"disclosures",limit));
 }
 @PostMapping("/disclosures")
 public ResponseEntity<Object> disclosure(@AuthenticationPrincipal Jwt jwt,
       @RequestHeader(value="Idempotency-Key",required=false)String key,
       @RequestBody Disclosure req){
  return reply(store.requestDisclosure(actor(jwt),key(key),req));
 }
 @PostMapping("/disclosures/{id}/review")
 public ResponseEntity<Object> review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
       @RequestHeader(value="Idempotency-Key",required=false)String key,
       @RequestBody Decision req){
  return reply(store.reviewDisclosure(actor(jwt),key(key),id,req));
 }
 @GetMapping("/cases")
 public ResponseEntity<List<Map<String,Object>>> cases(@AuthenticationPrincipal Jwt jwt,
       @RequestParam(defaultValue="25")int limit){
  return ok(store.listing(actor(jwt),"privacy_cases",limit));
 }
 @PostMapping("/cases")
 public ResponseEntity<Object> privacyCase(@AuthenticationPrincipal Jwt jwt,
       @RequestHeader(value="Idempotency-Key",required=false)String key,
       @RequestBody PrivacyCase req){
  return reply(store.openCase(actor(jwt),key(key),req));
 }
 @PostMapping("/cases/{id}/close")
 public ResponseEntity<Object> closeCase(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
       @RequestHeader(value="Idempotency-Key",required=false)String key,
       @RequestBody Decision req){
  return reply(store.closeCase(actor(jwt),key(key),id,req));
 }
}
