package org.synqoria.auditcore;

import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static org.synqoria.auditcore.Rules.*;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditApi{
 private final AuditStore store;private final Clock clock;
 public AuditApi(AuditStore store,Clock clock){this.store=store;this.clock=clock;}
 private Actor actor(Jwt jwt){return AuditPlatform.actor(jwt,clock.instant());}
 private static UUID key(String value){
  try{return UUID.fromString(Objects.requireNonNull(value));}
  catch(IllegalArgumentException|NullPointerException e){throw new Problem("idempotency_key_required");}
 }
 private static ResponseEntity<Object> reply(AuditStore.Reply r){
  return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());
 }
 private static <T>ResponseEntity<T> ok(T value){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(value);
 }
 @GetMapping("/events")
 public ResponseEntity<List<Map<String,Object>>> events(@AuthenticationPrincipal Jwt jwt,
    @RequestParam(defaultValue="25")int limit){
  return ok(store.list(actor(jwt),"evidence_events",limit));
 }
 @GetMapping("/events/{id}")
 public ResponseEntity<Map<String,Object>> event(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
  return ok(store.detail(actor(jwt),id));
 }
 @GetMapping("/streams/{id}/timeline")
 public ResponseEntity<List<Map<String,Object>>> timeline(@AuthenticationPrincipal Jwt jwt,
    @PathVariable UUID id,@RequestParam(defaultValue="25")int limit){
  return ok(store.timeline(actor(jwt),id,limit));
 }
 @GetMapping("/streams/{id}/verify")
 public ResponseEntity<Map<String,Object>> verify(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
  return ok(store.verify(actor(jwt),id));
 }
 @PostMapping("/ingest")
 public ResponseEntity<Object> ingest(@AuthenticationPrincipal Jwt jwt,
    @RequestHeader(value="Idempotency-Key",required=false)String key,
    @RequestBody Ingest body){
  return reply(store.ingest(actor(jwt),key(key),body));
 }
 @GetMapping("/exports")
 public ResponseEntity<List<Map<String,Object>>> exports(@AuthenticationPrincipal Jwt jwt,
    @RequestParam(defaultValue="25")int limit){
  return ok(store.list(actor(jwt),"export_requests",limit));
 }
 @PostMapping("/exports")
 public ResponseEntity<Object> requestExport(@AuthenticationPrincipal Jwt jwt,
    @RequestHeader(value="Idempotency-Key",required=false)String key,
    @RequestBody ExportRequest request){
  return reply(store.requestExport(actor(jwt),key(key),request));
 }
 @PostMapping("/exports/{id}/review")
 public ResponseEntity<Object> reviewExport(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
    @RequestHeader(value="Idempotency-Key",required=false)String key,
    @RequestBody Decision decision){
  return reply(store.reviewExport(actor(jwt),key(key),id,decision));
 }
 @GetMapping("/exports/{id}/manifest")
 public ResponseEntity<Map<String,Object>> manifest(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
  return ok(store.manifest(actor(jwt),id));
 }
 @GetMapping("/retention/holds")
 public ResponseEntity<List<Map<String,Object>>> holds(@AuthenticationPrincipal Jwt jwt,
    @RequestParam(defaultValue="25")int limit){
  return ok(store.list(actor(jwt),"retention_holds",limit));
 }
 @GetMapping("/retention/reviews")
 public ResponseEntity<List<Map<String,Object>>> retention(@AuthenticationPrincipal Jwt jwt,
    @RequestParam(defaultValue="25")int limit){
  return ok(store.list(actor(jwt),"retention_reviews",limit));
 }
 @PostMapping("/retention/reviews")
 public ResponseEntity<Object> requestRetention(@AuthenticationPrincipal Jwt jwt,
    @RequestHeader(value="Idempotency-Key",required=false)String key,
    @RequestBody RetentionReview request){
  return reply(store.requestRetention(actor(jwt),key(key),request));
 }
 @PostMapping("/retention/reviews/{id}/review")
 public ResponseEntity<Object> reviewRetention(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
    @RequestHeader(value="Idempotency-Key",required=false)String key,
    @RequestBody Decision decision){
  return reply(store.reviewRetention(actor(jwt),key(key),id,decision));
 }
}
