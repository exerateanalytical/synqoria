package org.synqoria.offline;

import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.offline.Db.Reply;
import org.synqoria.offline.domain.Domain.*;

/** Versioned API /api/v1/offline. Contract: contracts/openapi/synqoria-m38-offline-v1.yaml (generated from this class). */
@RestController
@RequestMapping("/api/v1/offline")
public class Api {
 private final Clock clock;
 private final SyncService sync;
 private final TransferService transfers;
 public Api(Clock clock,SyncService sync,TransferService transfers){this.clock=clock;this.sync=sync;this.transfers=transfers;}
 private Actor actor(Jwt j){return Platform.actorFrom(j,clock.instant());}
 private static UUID key(String h){
  if(h==null)throw new Violation("idempotency_key_required");
  try{return UUID.fromString(h);}catch(IllegalArgumentException e){throw new Violation("idempotency_key_required");}
 }
 private static ResponseEntity<Object> out(Reply r){return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());}
 private static <T> ResponseEntity<T> ok(T b){return ResponseEntity.ok().header("Cache-Control","no-store").body(b);}
 private static <T> T req(T b){if(b==null)throw new Violation("malformed_request");return b;}
 private static final String K="Idempotency-Key";

 // ---- contracts
 @GetMapping("/contracts") public ResponseEntity<List<Map<String,Object>>> contracts(@AuthenticationPrincipal Jwt j){return ok(sync.contracts(actor(j)));}
 @PostMapping("/contracts") public ResponseEntity<Object> contractCreate(@RequestHeader(value=K,required=false)String k,@RequestBody SyncService.ContractRequest b,@AuthenticationPrincipal Jwt j){return out(sync.createContract(actor(j),key(k),req(b)));}
 @PostMapping("/contracts/{id}/decision") public ResponseEntity<Object> contractDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody SyncService.Decision b,@AuthenticationPrincipal Jwt j){return out(sync.decideContract(actor(j),key(k),id,req(b)));}
 // ---- devices
 @GetMapping("/devices") public ResponseEntity<List<Map<String,Object>>> devices(@AuthenticationPrincipal Jwt j){return ok(sync.devices(actor(j)));}
 @PostMapping("/devices") public ResponseEntity<Object> deviceRegister(@RequestHeader(value=K,required=false)String k,@RequestBody SyncService.DeviceRequest b,@AuthenticationPrincipal Jwt j){return out(sync.registerDevice(actor(j),key(k),req(b)));}
 @PostMapping("/devices/{id}/revocation") public ResponseEntity<Object> deviceRevoke(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody SyncService.Revoke b,@AuthenticationPrincipal Jwt j){return out(sync.revokeDevice(actor(j),key(k),id,req(b)));}
 @GetMapping("/devices/{id}/cursor") public ResponseEntity<Map<String,Object>> deviceCursor(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(sync.cursor(actor(j),id));}
 // ---- authoritative heads (published by the owning domain)
 @GetMapping("/heads") public ResponseEntity<Map<String,Object>> heads(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(sync.heads(actor(j),page,size));}
 @PostMapping("/heads") public ResponseEntity<Object> headRegister(@RequestHeader(value=K,required=false)String k,@RequestBody SyncService.HeadRequest b,@AuthenticationPrincipal Jwt j){return out(sync.registerHead(actor(j),key(k),req(b)));}
 @PostMapping("/heads/{id}/revisions") public ResponseEntity<Object> headRevision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody SyncService.RevisionRequest b,@AuthenticationPrincipal Jwt j){return out(sync.publishRevision(actor(j),key(k),id,req(b)));}
 // ---- sync protocol and queue
 @PostMapping("/sync/batches") public ResponseEntity<Object> batch(@RequestHeader(value=K,required=false)String k,@RequestBody SyncService.BatchRequest b,@AuthenticationPrincipal Jwt j){return out(sync.submitBatch(actor(j),key(k),req(b)));}
 @GetMapping("/status") public ResponseEntity<Map<String,Object>> status(@AuthenticationPrincipal Jwt j){return ok(sync.status(actor(j)));}
 @GetMapping("/commands") public ResponseEntity<Map<String,Object>> commands(@RequestParam(required=false)String status,@RequestParam(required=false)UUID deviceId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(sync.commands(actor(j),status,deviceId,page,size));}
 @GetMapping("/commands/{id}") public ResponseEntity<Map<String,Object>> command(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(sync.command(actor(j),id));}
 // ---- conflicts
 @GetMapping("/conflicts") public ResponseEntity<Map<String,Object>> conflicts(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(sync.conflicts(actor(j),status,page,size));}
 @GetMapping("/conflicts/{id}") public ResponseEntity<Map<String,Object>> conflict(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(sync.conflict(actor(j),id));}
 @PostMapping("/conflicts/{id}/resolution") public ResponseEntity<Object> conflictResolve(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody SyncService.Resolution b,@AuthenticationPrincipal Jwt j){return out(sync.resolve(actor(j),key(k),id,req(b)));}
 // ---- ownership transfers
 @GetMapping("/transfers") public ResponseEntity<Map<String,Object>> transferList(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(transfers.list(actor(j),status,page,size));}
 @PostMapping("/transfers") public ResponseEntity<Object> transferPropose(@RequestHeader(value=K,required=false)String k,@RequestBody TransferService.Propose b,@AuthenticationPrincipal Jwt j){return out(transfers.propose(actor(j),key(k),req(b)));}
 @PostMapping("/transfers/{id}/freeze") public ResponseEntity<Object> transferFreeze(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TransferService.Step b,@AuthenticationPrincipal Jwt j){return out(transfers.freeze(actor(j),key(k),id,req(b)));}
 @PostMapping("/transfers/{id}/fence") public ResponseEntity<Object> transferFence(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TransferService.Fence b,@AuthenticationPrincipal Jwt j){return out(transfers.fence(actor(j),key(k),id,req(b)));}
 @PostMapping("/transfers/{id}/decision") public ResponseEntity<Object> transferDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TransferService.Decide b,@AuthenticationPrincipal Jwt j){return out(transfers.decide(actor(j),key(k),id,req(b)));}
 @PostMapping("/transfers/{id}/completion") public ResponseEntity<Object> transferComplete(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TransferService.Step b,@AuthenticationPrincipal Jwt j){return out(transfers.complete(actor(j),key(k),id,req(b)));}
 @PostMapping("/transfers/{id}/abort") public ResponseEntity<Object> transferAbort(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TransferService.Abort b,@AuthenticationPrincipal Jwt j){return out(transfers.abort(actor(j),key(k),id,req(b)));}
 // ---- history
 @GetMapping("/reconciliations") public ResponseEntity<Map<String,Object>> history(@RequestParam(required=false)String outcome,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(sync.history(actor(j),outcome,page,size));}
}
