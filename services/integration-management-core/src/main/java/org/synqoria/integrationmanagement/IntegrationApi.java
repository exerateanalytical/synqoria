package org.synqoria.integrationmanagement;

import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static org.synqoria.integrationmanagement.IntegrationApplication.*;
import static org.synqoria.integrationmanagement.IntegrationStore.*;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationApi{
 private final IntegrationStore store;private final Clock clock;
 public IntegrationApi(IntegrationStore store,Clock clock){this.store=store;this.clock=clock;}
 private Actor a(Jwt jwt){return actor(jwt,clock.instant());}
 private UUID key(String text){
  try{return UUID.fromString(Objects.requireNonNull(text));}
  catch(IllegalArgumentException|NullPointerException e){throw new Problem("idempotency_key_required");}
 }
 private static ResponseEntity<Object> reply(Reply result){
  return ResponseEntity.status(result.status()).header("Cache-Control","no-store").body(result.body());
 }
 @GetMapping("/{collection}")
 public ResponseEntity<List<Map<String,Object>>> list(@AuthenticationPrincipal Jwt jwt,
  @PathVariable String collection,@RequestParam(defaultValue="25")int limit){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(store.list(a(jwt),collection,limit));
 }
 @PostMapping("/partners")
 public ResponseEntity<Object> partner(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Partner req){
  return reply(store.partner(a(jwt),key(key),req));
 }
 @PostMapping("/partners/{id}/{action}")
 public ResponseEntity<Object> partnerAction(@AuthenticationPrincipal Jwt jwt,
  @PathVariable UUID id,@PathVariable String action,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Version req){
  return reply(store.partnerAction(a(jwt),key(key),id,action,req));
 }
 @PostMapping("/api_products")
 public ResponseEntity<Object> product(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ApiProduct req){
  return reply(store.product(a(jwt),key(key),req));
 }
 @PostMapping("/api_products/{id}/publish")
 public ResponseEntity<Object> publish(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Version req){
  return reply(store.publish(a(jwt),key(key),id,req));
 }
 @PostMapping("/developer_apps")
 public ResponseEntity<Object> application(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody DeveloperApp req){
  return reply(store.application(a(jwt),key(key),req));
 }
 @PostMapping("/developer_apps/{id}/{action}")
 public ResponseEntity<Object> appAction(@AuthenticationPrincipal Jwt jwt,
  @PathVariable UUID id,@PathVariable String action,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Version req){
  return reply(store.appAction(a(jwt),key(key),id,action,req));
 }
 @PostMapping("/mapping_versions")
 public ResponseEntity<Object> mapping(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Mapping req){
  return reply(store.mapping(a(jwt),key(key),req));
 }
 @PostMapping("/mapping_versions/{id}/{action}")
 public ResponseEntity<Object> mappingAction(@AuthenticationPrincipal Jwt jwt,
  @PathVariable UUID id,@PathVariable String action,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Version req){
  return reply(store.mappingAction(a(jwt),key(key),id,action,req));
 }
 @PostMapping("/messages/ingest")
 public ResponseEntity<Object> ingest(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Ingest req){
  return reply(store.ingest(a(jwt),key(key),req));
 }
 @PostMapping("/messages/{id}/review")
 public ResponseEntity<Object> reviewMessage(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Version req){
  return reply(store.reviewMessage(a(jwt),key(key),id,req));
 }
 @PostMapping("/replay_requests")
 public ResponseEntity<Object> replay(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Replay req){
  return reply(store.replay(a(jwt),key(key),req));
 }
 @PostMapping("/replay_requests/{id}/review")
 public ResponseEntity<Object> reviewReplay(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Decision req){
  return reply(store.reviewReplay(a(jwt),key(key),id,req));
 }
}
