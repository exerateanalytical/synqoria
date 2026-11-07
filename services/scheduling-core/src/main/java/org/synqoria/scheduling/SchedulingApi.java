package org.synqoria.scheduling;

import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static org.synqoria.scheduling.SchedulingApplication.*;
import static org.synqoria.scheduling.SchedulingStore.*;

@RestController
@RequestMapping("/api/v1/scheduling")
public class SchedulingApi {
 private final SchedulingStore store;private final Clock clock;
 public SchedulingApi(SchedulingStore store,Clock clock){this.store=store;this.clock=clock;}
 private Actor a(Jwt jwt){return actor(jwt,clock.instant());}
 private UUID key(String value){
  try{return UUID.fromString(Objects.requireNonNull(value));}
  catch(IllegalArgumentException|NullPointerException e){throw new Denied("idempotency_key_required");}
 }
 private static ResponseEntity<Object> reply(Reply r){
  return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());
 }
 @GetMapping("/{collection}")
 public ResponseEntity<List<Map<String,Object>>> list(@AuthenticationPrincipal Jwt jwt,
   @PathVariable String collection,@RequestParam(defaultValue="25") int limit){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(store.list(a(jwt),collection,limit));
 }
 @PostMapping("/resources")
 public ResponseEntity<Object> resource(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Resource request){
  return reply(store.resource(a(jwt),key(key),request));
 }
 @PostMapping("/slots")
 public ResponseEntity<Object> slot(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Slot request){
  return reply(store.slot(a(jwt),key(key),request));
 }
 @PostMapping("/appointments")
 public ResponseEntity<Object> book(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Booking request){
  return reply(store.book(a(jwt),key(key),request));
 }
 @PostMapping("/appointments/{id}/{action}")
 public ResponseEntity<Object> transition(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
  @PathVariable String action,@RequestHeader(value="Idempotency-Key",required=false)String key,
  @RequestBody Version request){
  return reply(store.appointmentAction(a(jwt),key(key),id,action,request));
 }
 @PostMapping("/appointments/{id}/reschedule")
 public ResponseEntity<Object> move(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Move request){
  return reply(store.reschedule(a(jwt),key(key),id,request));
 }
 @PostMapping("/waitlist")
 public ResponseEntity<Object> waitlist(@AuthenticationPrincipal Jwt jwt,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Wait request){
  return reply(store.waitlist(a(jwt),key(key),request));
 }
 @PostMapping("/waitlist/{id}/offer")
 public ResponseEntity<Object> offer(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Offer request){
  return reply(store.offer(a(jwt),key(key),id,request));
 }
 @PostMapping("/waitlist/{id}/accept")
 public ResponseEntity<Object> accept(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
  @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Version request){
  return reply(store.accept(a(jwt),key(key),id,request));
 }
}
