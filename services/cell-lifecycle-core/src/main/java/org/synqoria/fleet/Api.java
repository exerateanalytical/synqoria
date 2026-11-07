package org.synqoria.fleet;

import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.fleet.Db.Reply;
import org.synqoria.fleet.domain.Domain.*;

/** Versioned API /api/v1/fleet (management plane). Contract: contracts/openapi/synqoria-m39-fleet-v1.yaml (generated from this class). */
@RestController
@RequestMapping("/api/v1/fleet")
public class Api {
 private final Clock clock;
 private final FleetService fleet;
 private final RoutingService routing;
 private final ReleaseService releases;
 private final MigrationService migrations;
 public Api(Clock clock,FleetService fleet,RoutingService routing,ReleaseService releases,MigrationService migrations){this.clock=clock;this.fleet=fleet;this.routing=routing;this.releases=releases;this.migrations=migrations;}
 private Actor actor(Jwt j){return Platform.actorFrom(j,clock.instant());}
 private static UUID key(String h){
  if(h==null)throw new Violation("idempotency_key_required");
  try{return UUID.fromString(h);}catch(IllegalArgumentException e){throw new Violation("idempotency_key_required");}
 }
 private static ResponseEntity<Object> out(Reply r){return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());}
 private static <T> ResponseEntity<T> ok(T b){return ResponseEntity.ok().header("Cache-Control","no-store").body(b);}
 private static <T> T req(T b){if(b==null)throw new Violation("malformed_request");return b;}
 private static final String K="Idempotency-Key";

 // ---- fleet views
 @GetMapping("/tenants") public ResponseEntity<Map<String,Object>> tenants(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(fleet.tenants(actor(j),page,size));}
 @GetMapping("/cells") public ResponseEntity<Map<String,Object>> cells(@RequestParam(required=false)String state,@RequestParam(required=false)UUID tenantId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(fleet.cells(actor(j),state,tenantId,page,size));}
 @GetMapping("/cells/{id}") public ResponseEntity<Map<String,Object>> cell(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(fleet.cell(actor(j),id));}
 @PostMapping("/cells/{id}/transition") public ResponseEntity<Object> cellTransition(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FleetService.Transition b,@AuthenticationPrincipal Jwt j){return out(fleet.transition(actor(j),key(k),id,req(b)));}
 // ---- provisioning
 @GetMapping("/provisioning-requests") public ResponseEntity<Map<String,Object>> provisioningList(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(fleet.provisioningList(actor(j),status,page,size));}
 @PostMapping("/provisioning-requests") public ResponseEntity<Object> provisioningRequest(@RequestHeader(value=K,required=false)String k,@RequestBody FleetService.ProvisionRequest b,@AuthenticationPrincipal Jwt j){return out(fleet.requestTenant(actor(j),key(k),req(b)));}
 @PostMapping("/provisioning-requests/{id}/decision") public ResponseEntity<Object> provisioningDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FleetService.Decision b,@AuthenticationPrincipal Jwt j){return out(fleet.decideProvisioning(actor(j),key(k),id,req(b)));}
 @PostMapping("/provisioning-requests/{id}/application") public ResponseEntity<Object> provisioningApply(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FleetService.ApplyRequest b,@AuthenticationPrincipal Jwt j){return out(fleet.applyProvisioning(actor(j),key(k),id,req(b)));}
 @PostMapping("/provisioning-requests/{id}/rollback") public ResponseEntity<Object> provisioningRollback(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FleetService.RollbackRequest b,@AuthenticationPrincipal Jwt j){return out(fleet.rollbackProvisioning(actor(j),key(k),id,req(b)));}
 // ---- qualification and health
 @GetMapping("/cells/{id}/qualifications") public ResponseEntity<List<Map<String,Object>>> qualifications(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(fleet.qualifications(actor(j),id));}
 @PostMapping("/cells/{id}/qualifications") public ResponseEntity<Object> qualificationStart(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(fleet.startQualification(actor(j),key(k),id));}
 @PostMapping("/qualifications/{id}/checks") public ResponseEntity<Object> qualificationCheck(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FleetService.CheckRequest b,@AuthenticationPrincipal Jwt j){return out(fleet.recordCheck(actor(j),key(k),id,req(b)));}
 @PostMapping("/qualifications/{id}/completion") public ResponseEntity<Object> qualificationComplete(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FleetService.Complete b,@AuthenticationPrincipal Jwt j){return out(fleet.completeQualification(actor(j),key(k),id,req(b)));}
 @PostMapping("/cells/{id}/health") public ResponseEntity<Object> health(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody FleetService.HealthReport b,@AuthenticationPrincipal Jwt j){return out(fleet.reportHealth(actor(j),key(k),id,req(b)));}
 // ---- routing directory
 @GetMapping("/routes") public ResponseEntity<Map<String,Object>> routes(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(routing.routes(actor(j),page,size));}
 @GetMapping("/routing/{routeKey}") public ResponseEntity<Map<String,Object>> routingLookup(@PathVariable String routeKey,@AuthenticationPrincipal Jwt j){return ok(routing.lookup(actor(j),routeKey));}
 @GetMapping("/routing/{routeKey}/fence") public ResponseEntity<Map<String,Object>> routingFence(@PathVariable String routeKey,@AuthenticationPrincipal Jwt j){return ok(routing.routingFence(actor(j),routeKey));}
 @PostMapping("/routes") public ResponseEntity<Object> routeCreate(@RequestHeader(value=K,required=false)String k,@RequestBody RoutingService.RouteRequest b,@AuthenticationPrincipal Jwt j){return out(routing.createRoute(actor(j),key(k),req(b)));}
 @PostMapping("/routes/{id}/switch") public ResponseEntity<Object> routeSwitch(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody RoutingService.Switch b,@AuthenticationPrincipal Jwt j){return out(routing.switchRoute(actor(j),key(k),id,req(b)));}
 // ---- releases and rollouts
 @GetMapping("/releases") public ResponseEntity<Map<String,Object>> releaseList(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(releases.manifests(actor(j),page,size));}
 @PostMapping("/releases") public ResponseEntity<Object> releaseCreate(@RequestHeader(value=K,required=false)String k,@RequestBody ReleaseService.ManifestRequest b,@AuthenticationPrincipal Jwt j){return out(releases.createManifest(actor(j),key(k),req(b)));}
 @PostMapping("/releases/{id}/decision") public ResponseEntity<Object> releaseDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ReleaseService.Decision b,@AuthenticationPrincipal Jwt j){return out(releases.decideManifest(actor(j),key(k),id,req(b)));}
 @GetMapping("/rollouts") public ResponseEntity<Map<String,Object>> rolloutList(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(releases.rollouts(actor(j),page,size));}
 @GetMapping("/rollouts/{id}") public ResponseEntity<Map<String,Object>> rollout(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(releases.rolloutDetail(actor(j),id));}
 @PostMapping("/rollouts") public ResponseEntity<Object> rolloutStart(@RequestHeader(value=K,required=false)String k,@RequestBody ReleaseService.RolloutRequest b,@AuthenticationPrincipal Jwt j){return out(releases.startRollout(actor(j),key(k),req(b)));}
 @PostMapping("/rollouts/{id}/cells/{cellId}/evidence") public ResponseEntity<Object> rolloutEvidence(@PathVariable UUID id,@PathVariable UUID cellId,@RequestHeader(value=K,required=false)String k,@RequestBody ReleaseService.Evidence b,@AuthenticationPrincipal Jwt j){return out(releases.recordEvidence(actor(j),key(k),id,cellId,req(b)));}
 @PostMapping("/rollouts/{id}/advance") public ResponseEntity<Object> rolloutAdvance(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ReleaseService.Advance b,@AuthenticationPrincipal Jwt j){return out(releases.advance(actor(j),key(k),id,req(b)));}
 @PostMapping("/rollouts/{id}/stop") public ResponseEntity<Object> rolloutStop(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ReleaseService.Stop b,@AuthenticationPrincipal Jwt j){return out(releases.stop(actor(j),key(k),id,req(b)));}
 // ---- migrations
 @GetMapping("/migrations") public ResponseEntity<Map<String,Object>> migrationList(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(migrations.list(actor(j),page,size));}
 @GetMapping("/migrations/{id}") public ResponseEntity<Map<String,Object>> migration(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(migrations.get(actor(j),id));}
 @PostMapping("/migrations") public ResponseEntity<Object> migrationPlan(@RequestHeader(value=K,required=false)String k,@RequestBody MigrationService.Plan b,@AuthenticationPrincipal Jwt j){return out(migrations.plan(actor(j),key(k),req(b)));}
 @PostMapping("/migrations/{id}/steps") public ResponseEntity<Object> migrationStep(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody MigrationService.StepRequest b,@AuthenticationPrincipal Jwt j){return out(migrations.step(actor(j),key(k),id,req(b)));}
 @PostMapping("/migrations/{id}/rollback") public ResponseEntity<Object> migrationRollback(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody MigrationService.Rollback b,@AuthenticationPrincipal Jwt j){return out(migrations.rollback(actor(j),key(k),id,req(b)));}
}
