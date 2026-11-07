package org.synqoria.nutrition;

import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.nutrition.Db.Reply;
import org.synqoria.nutrition.domain.Domain.*;

/** Versioned API /api/v1/nutrition. Contract: contracts/openapi/synqoria-m26-nutrition-v1.yaml. */
@RestController
@RequestMapping("/api/v1/nutrition")
public class Api {
 private final Clock clock;
 private final OrderService orders;
 private final ProductionService production;
 private final DeliveryService delivery;
 private final WorklistService worklist;

 public Api(Clock clock,OrderService orders,ProductionService production,DeliveryService delivery,WorklistService worklist){
  this.clock=clock;this.orders=orders;this.production=production;this.delivery=delivery;this.worklist=worklist;
 }
 private Actor actor(Jwt jwt){return org.synqoria.nutrition.Platform.actorFrom(jwt,clock.instant());}
 private static UUID key(String header){
  if(header==null)throw new Violation("idempotency_key_required");
  try{return UUID.fromString(header);}catch(IllegalArgumentException e){throw new Violation("idempotency_key_required");}
 }
 private static ResponseEntity<Object> out(Reply r){
  return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());
 }
 private static <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
 private static <T> T req(T body){if(body==null)throw new Violation("malformed_request");return body;}

 // ----------------------------------------------------------------- consumed clinical contracts
 @PutMapping("/feed/patients/{patient}/context")
 public ResponseEntity<Object> feedPatient(@PathVariable UUID patient,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody OrderService.PatientContext b,@AuthenticationPrincipal Jwt jwt){return out(orders.feedPatient(actor(jwt),key(k),patient,req(b)));}
 @PutMapping("/feed/patients/{patient}/allergies")
 public ResponseEntity<Object> feedAllergies(@PathVariable UUID patient,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody OrderService.Allergies b,@AuthenticationPrincipal Jwt jwt){return out(orders.feedAllergies(actor(jwt),key(k),patient,req(b)));}
 @PutMapping("/feed/fasting/{instruction}")
 public ResponseEntity<Object> feedFasting(@PathVariable UUID instruction,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody OrderService.FastingFeed b,@AuthenticationPrincipal Jwt jwt){return out(orders.feedFasting(actor(jwt),key(k),instruction,req(b)));}

 // ----------------------------------------------------------------- M26-S01 worklist
 @GetMapping("/worklist")
 public ResponseEntity<Map<String,Object>> worklist(@RequestParam(required=false)String ward,@RequestParam(required=false)String mealStatus,
   @RequestParam(required=false)String orderStatus,@RequestParam(required=false)Boolean allergy,@RequestParam(required=false)Boolean fasting,
   @RequestParam(required=false)Boolean exception,@RequestParam(required=false)String q,@RequestParam(required=false)String sort,
   @RequestParam(required=false)String dir,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,
   @AuthenticationPrincipal Jwt jwt){
  return ok(worklist.list(actor(jwt),new WorklistService.Query(ward,mealStatus,orderStatus,allergy,fasting,exception,q,sort,dir,page,size)));
 }
 @GetMapping("/worklist/{patient}")
 public ResponseEntity<Map<String,Object>> worklistDetail(@PathVariable UUID patient,@AuthenticationPrincipal Jwt jwt){
  return ok(worklist.detail(actor(jwt),patient));
 }

 // ----------------------------------------------------------------- M26-S02 dietary orders
 @PostMapping("/orders")
 public ResponseEntity<Object> prescribe(@RequestHeader(value="Idempotency-Key",required=false)String k,@RequestBody OrderService.OrderRequest b,
   @AuthenticationPrincipal Jwt jwt){return out(orders.prescribe(actor(jwt),key(k),req(b)));}
 @GetMapping("/orders")
 public ResponseEntity<Map<String,Object>> listOrders(@RequestParam(required=false)UUID patientRef,@RequestParam(required=false)String status,
   @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt jwt){
  return ok(orders.list(actor(jwt),patientRef,status,page,size));
 }
 @GetMapping("/orders/{id}")
 public ResponseEntity<Map<String,Object>> order(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ok(orders.get(actor(jwt),id));}
 @GetMapping("/orders/{id}/history")
 public ResponseEntity<List<Map<String,Object>>> history(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ok(orders.history(actor(jwt),id));}
 @PostMapping("/orders/{id}/amend")
 public ResponseEntity<Object> amend(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody OrderService.AmendRequest b,@AuthenticationPrincipal Jwt jwt){return out(orders.amend(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/orders/{id}/approve")
 public ResponseEntity<Object> approve(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody OrderService.TransitionRequest b,@AuthenticationPrincipal Jwt jwt){return out(orders.approve(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/orders/{id}/cancel")
 public ResponseEntity<Object> cancel(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody OrderService.TransitionRequest b,@AuthenticationPrincipal Jwt jwt){return out(orders.cancel(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/orders/{id}/revoke")
 public ResponseEntity<Object> revoke(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody OrderService.TransitionRequest b,@AuthenticationPrincipal Jwt jwt){return out(orders.revoke(actor(jwt),key(k),id,req(b)));}

 // ----------------------------------------------------------------- M26-S03 production
 @GetMapping("/batches")
 public ResponseEntity<Map<String,Object>> batches(@RequestParam(required=false)String ward,@RequestParam(required=false)String status,
   @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt jwt){
  return ok(production.list(actor(jwt),ward,status,page,size));
 }
 @PostMapping("/batches")
 public ResponseEntity<Object> createBatch(@RequestHeader(value="Idempotency-Key",required=false)String k,@RequestBody ProductionService.BatchRequest b,
   @AuthenticationPrincipal Jwt jwt){return out(production.create(actor(jwt),key(k),req(b)));}
 @GetMapping("/batches/{id}")
 public ResponseEntity<Map<String,Object>> batch(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ok(production.get(actor(jwt),id));}
 @PostMapping("/batches/{id}/release")
 public ResponseEntity<Object> release(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody ProductionService.Transition b,@AuthenticationPrincipal Jwt jwt){return out(production.release(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/batches/{id}/prepared")
 public ResponseEntity<Object> prepared(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody ProductionService.Transition b,@AuthenticationPrincipal Jwt jwt){return out(production.prepared(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/batches/{id}/dispatch-approval")
 public ResponseEntity<Object> dispatchApproval(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody ProductionService.Transition b,@AuthenticationPrincipal Jwt jwt){return out(production.approveDispatch(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/batches/{id}/cancel")
 public ResponseEntity<Object> cancelBatch(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody ProductionService.Transition b,@AuthenticationPrincipal Jwt jwt){return out(production.cancel(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/substitutions")
 public ResponseEntity<Object> propose(@RequestHeader(value="Idempotency-Key",required=false)String k,@RequestBody ProductionService.SubstitutionRequest b,
   @AuthenticationPrincipal Jwt jwt){return out(production.propose(actor(jwt),key(k),req(b)));}
 @PostMapping("/substitutions/{id}/decision")
 public ResponseEntity<Object> decide(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody ProductionService.Decision b,@AuthenticationPrincipal Jwt jwt){return out(production.decide(actor(jwt),key(k),id,req(b)));}

 // ----------------------------------------------------------------- M26-S04 delivery
 @GetMapping("/trays")
 public ResponseEntity<Map<String,Object>> trays(@RequestParam(required=false)String ward,@RequestParam(required=false)String status,
   @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt jwt){
  return ok(delivery.listTrays(actor(jwt),ward,status,page,size));
 }
 @GetMapping("/trays/awaiting")
 public ResponseEntity<List<Map<String,Object>>> awaiting(@RequestParam(required=false)String ward,@AuthenticationPrincipal Jwt jwt){
  return ok(delivery.awaitingTray(actor(jwt),ward));
 }
 @PostMapping("/trays")
 public ResponseEntity<Object> load(@RequestHeader(value="Idempotency-Key",required=false)String k,@RequestBody DeliveryService.LoadRequest b,
   @AuthenticationPrincipal Jwt jwt){return out(delivery.load(actor(jwt),key(k),req(b)));}
 @GetMapping("/trays/{id}")
 public ResponseEntity<Map<String,Object>> tray(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return ok(delivery.get(actor(jwt),id));}
 @PostMapping("/trays/{id}/dispatch")
 public ResponseEntity<Object> dispatch(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody DeliveryService.Transition b,@AuthenticationPrincipal Jwt jwt){return out(delivery.dispatch(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/trays/{id}/deliver")
 public ResponseEntity<Object> deliver(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody DeliveryService.DeliverRequest b,@AuthenticationPrincipal Jwt jwt){return out(delivery.deliver(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/trays/{id}/acknowledge")
 public ResponseEntity<Object> acknowledge(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody DeliveryService.Transition b,@AuthenticationPrincipal Jwt jwt){return out(delivery.acknowledge(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/trays/{id}/return")
 public ResponseEntity<Object> returnTray(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody DeliveryService.Transition b,@AuthenticationPrincipal Jwt jwt){return out(delivery.returnTray(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/trays/{id}/exceptions")
 public ResponseEntity<Object> exception(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody DeliveryService.ExceptionRequest b,@AuthenticationPrincipal Jwt jwt){return out(delivery.reportException(actor(jwt),key(k),id,req(b)));}
 @GetMapping("/incidents")
 public ResponseEntity<Map<String,Object>> incidents(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,
   @RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt jwt){return ok(delivery.listIncidents(actor(jwt),status,page,size));}
 @PostMapping("/incidents/{id}/escalate")
 public ResponseEntity<Object> escalate(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody DeliveryService.EscalateRequest b,@AuthenticationPrincipal Jwt jwt){return out(delivery.escalate(actor(jwt),key(k),id,req(b)));}
 @PostMapping("/incidents/{id}/resolve")
 public ResponseEntity<Object> resolve(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String k,
   @RequestBody DeliveryService.ResolveRequest b,@AuthenticationPrincipal Jwt jwt){return out(delivery.resolve(actor(jwt),key(k),id,req(b)));}
}
