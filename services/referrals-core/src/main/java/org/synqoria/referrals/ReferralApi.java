package org.synqoria.referrals;
import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static org.synqoria.referrals.ReferralApplication.*;
import static org.synqoria.referrals.ReferralStore.*;

@RestController @RequestMapping("/api/v1/referrals")
public class ReferralApi{
 private final ReferralStore store;private final Clock clock;
 public ReferralApi(ReferralStore store,Clock clock){this.store=store;this.clock=clock;}
 private Actor a(Jwt jwt){return actor(jwt,clock.instant());}
 private static UUID key(String text){
  try{return UUID.fromString(Objects.requireNonNull(text));}
  catch(IllegalArgumentException|NullPointerException e){throw new Problem("idempotency_key_required");}
 }
 private static ResponseEntity<Object> reply(Reply r){
  return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());
 }
 @GetMapping
 public ResponseEntity<List<Map<String,Object>>> list(@AuthenticationPrincipal Jwt jwt,
     @RequestParam(defaultValue="25")int limit){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(store.list(a(jwt),limit));
 }
 @GetMapping("/{id}/history")
 public ResponseEntity<List<Map<String,Object>>> history(@AuthenticationPrincipal Jwt jwt,
     @PathVariable UUID id,@RequestParam(defaultValue="25")int limit){
  return ResponseEntity.ok().header("Cache-Control","no-store").body(store.transitions(a(jwt),id,limit));
 }
 @PostMapping
 public ResponseEntity<Object> create(@AuthenticationPrincipal Jwt jwt,
   @RequestHeader(value="Idempotency-Key",required=false)String key,
   @RequestBody Request req){
  return reply(store.create(a(jwt),key(key),req));
 }
 @PostMapping("/{id}/{action}")
 public ResponseEntity<Object> action(@AuthenticationPrincipal Jwt jwt,
   @PathVariable UUID id,@PathVariable String action,
   @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody Action payload){
  return reply(store.act(a(jwt),key(key),id,action,payload));
 }
}
