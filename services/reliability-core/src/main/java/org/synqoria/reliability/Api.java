package org.synqoria.reliability;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.reliability.Db.Reply;
import org.synqoria.reliability.domain.Domain.*;

/** Versioned API /api/v1/reliability. Contract: contracts/openapi/synqoria-m40-reliability-v1.yaml (generated from this class). */
@RestController
@RequestMapping("/api/v1/reliability")
public class Api {
 private final Clock clock;
 private final ObservabilityService obs;
 private final IncidentService incidents;
 private final BackupService backups;
 private final RecoveryService recoveries;
 public Api(Clock clock,ObservabilityService obs,IncidentService incidents,BackupService backups,RecoveryService recoveries){this.clock=clock;this.obs=obs;this.incidents=incidents;this.backups=backups;this.recoveries=recoveries;}
 private Actor actor(Jwt j){return Platform.actorFrom(j,clock.instant());}
 private static UUID key(String h){
  if(h==null)throw new Violation("idempotency_key_required");
  try{return UUID.fromString(h);}catch(IllegalArgumentException e){throw new Violation("idempotency_key_required");}
 }
 private static ResponseEntity<Object> out(Reply r){return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());}
 private static <T> ResponseEntity<T> ok(T b){return ResponseEntity.ok().header("Cache-Control","no-store").body(b);}
 private static <T> T req(T b){if(b==null)throw new Violation("malformed_request");return b;}
 private static final String K="Idempotency-Key";

 // ---- services, signals, health board
 @GetMapping("/health-board") public ResponseEntity<Map<String,Object>> healthBoard(@AuthenticationPrincipal Jwt j){return ok(obs.board(actor(j)));}
 @PostMapping("/services") public ResponseEntity<Object> serviceRegister(@RequestHeader(value=K,required=false)String k,@RequestBody ObservabilityService.ServiceRequest b,@AuthenticationPrincipal Jwt j){return out(obs.registerService(actor(j),key(k),req(b)));}
 @PostMapping("/services/{id}/signals") public ResponseEntity<Object> signal(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ObservabilityService.SignalRequest b,@AuthenticationPrincipal Jwt j){return out(obs.reportSignal(actor(j),key(k),id,req(b)));}
 // ---- telemetry
 @PostMapping("/telemetry/events") public ResponseEntity<Object> telemetryIngest(@RequestHeader(value=K,required=false)String k,@RequestBody ObservabilityService.EventBatch b,@AuthenticationPrincipal Jwt j){return out(obs.ingest(actor(j),key(k),req(b)));}
 @GetMapping("/telemetry/events") public ResponseEntity<Map<String,Object>> telemetryQuery(@RequestParam(required=false)String correlationId,@RequestParam(required=false)String serviceKey,@RequestParam(required=false)String level,
   @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to,
   @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@AuthenticationPrincipal Jwt j){return ok(obs.query(actor(j),correlationId,serviceKey,level,from,to,page,size));}
 @GetMapping("/telemetry/access-log") public ResponseEntity<Map<String,Object>> telemetryAccess(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@AuthenticationPrincipal Jwt j){return ok(obs.accessLog(actor(j),page,size));}
 // ---- incidents
 @GetMapping("/incidents") public ResponseEntity<Map<String,Object>> incidentList(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(incidents.list(actor(j),status,page,size));}
 @GetMapping("/incidents/{id}") public ResponseEntity<Map<String,Object>> incident(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(incidents.get(actor(j),id));}
 @PostMapping("/incidents") public ResponseEntity<Object> incidentDeclare(@RequestHeader(value=K,required=false)String k,@RequestBody IncidentService.Declare b,@AuthenticationPrincipal Jwt j){return out(incidents.declare(actor(j),key(k),req(b)));}
 @PostMapping("/incidents/{id}/acknowledgement") public ResponseEntity<Object> incidentAck(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IncidentService.Ack b,@AuthenticationPrincipal Jwt j){return out(incidents.acknowledge(actor(j),key(k),id,req(b)));}
 @PostMapping("/incidents/{id}/assignment") public ResponseEntity<Object> incidentAssign(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IncidentService.Assign b,@AuthenticationPrincipal Jwt j){return out(incidents.assign(actor(j),key(k),id,req(b)));}
 @PostMapping("/incidents/{id}/notes") public ResponseEntity<Object> incidentNote(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IncidentService.Note b,@AuthenticationPrincipal Jwt j){return out(incidents.note(actor(j),key(k),id,req(b)));}
 @PostMapping("/incidents/{id}/escalation") public ResponseEntity<Object> incidentEscalate(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IncidentService.Escalate b,@AuthenticationPrincipal Jwt j){return out(incidents.escalate(actor(j),key(k),id,req(b)));}
 @PostMapping("/incidents/{id}/status") public ResponseEntity<Object> incidentStatus(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IncidentService.StatusChange b,@AuthenticationPrincipal Jwt j){return out(incidents.status(actor(j),key(k),id,req(b)));}
 @PostMapping("/incidents/{id}/resolution") public ResponseEntity<Object> incidentResolve(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IncidentService.Resolve b,@AuthenticationPrincipal Jwt j){return out(incidents.resolve(actor(j),key(k),id,req(b)));}
 // ---- backups
 @GetMapping("/backups") public ResponseEntity<Map<String,Object>> backupList(@RequestParam(required=false)String kind,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(backups.list(actor(j),kind,status,page,size));}
 @GetMapping("/backups/{id}") public ResponseEntity<Map<String,Object>> backup(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(backups.get(actor(j),id));}
 @PostMapping("/backups") public ResponseEntity<Object> backupRecord(@RequestHeader(value=K,required=false)String k,@RequestBody BackupService.BackupRequest b,@AuthenticationPrincipal Jwt j){return out(backups.record(actor(j),key(k),req(b)));}
 @PostMapping("/backups/{id}/verification") public ResponseEntity<Object> backupVerify(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody BackupService.Verification b,@AuthenticationPrincipal Jwt j){return out(backups.verify(actor(j),key(k),id,req(b)));}
 @GetMapping("/wal-continuity") public ResponseEntity<Map<String,Object>> walContinuity(@RequestParam UUID baseBackupId,@RequestParam long targetLsn,@AuthenticationPrincipal Jwt j){return ok(backups.continuity(actor(j),baseBackupId,targetLsn));}
 // ---- recovery
 @GetMapping("/recoveries") public ResponseEntity<Map<String,Object>> recoveryList(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(recoveries.list(actor(j),page,size));}
 @GetMapping("/recoveries/{id}") public ResponseEntity<Map<String,Object>> recovery(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(recoveries.get(actor(j),id));}
 @GetMapping("/recovery-measurements") public ResponseEntity<Map<String,Object>> measurements(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(recoveries.measurements(actor(j),page,size));}
 @PostMapping("/recoveries") public ResponseEntity<Object> recoveryPlan(@RequestHeader(value=K,required=false)String k,@RequestBody RecoveryService.Plan b,@AuthenticationPrincipal Jwt j){return out(recoveries.plan(actor(j),key(k),req(b)));}
 @PostMapping("/recoveries/{id}/authorization") public ResponseEntity<Object> recoveryAuthorize(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody RecoveryService.Authorize b,@AuthenticationPrincipal Jwt j){return out(recoveries.authorize(actor(j),key(k),id,req(b)));}
 @PostMapping("/recoveries/{id}/steps") public ResponseEntity<Object> recoveryStep(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody RecoveryService.StepRequest b,@AuthenticationPrincipal Jwt j){return out(recoveries.step(actor(j),key(k),id,req(b)));}
 @PostMapping("/recoveries/{id}/validation") public ResponseEntity<Object> recoveryValidate(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody RecoveryService.ValidationItem b,@AuthenticationPrincipal Jwt j){return out(recoveries.validate(actor(j),key(k),id,req(b)));}
 @PostMapping("/recoveries/{id}/reopening") public ResponseEntity<Object> recoveryReopen(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody RecoveryService.Reopen b,@AuthenticationPrincipal Jwt j){return out(recoveries.reopen(actor(j),key(k),id,req(b)));}
 @PostMapping("/recoveries/{id}/abort") public ResponseEntity<Object> recoveryAbort(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody RecoveryService.Abort b,@AuthenticationPrincipal Jwt j){return out(recoveries.abort(actor(j),key(k),id,req(b)));}
}
