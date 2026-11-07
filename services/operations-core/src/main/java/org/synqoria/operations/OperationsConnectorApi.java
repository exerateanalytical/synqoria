package org.synqoria.operations;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;

/**
 * Exact authenticated integration handoffs, no arbitrary URL or dynamic SQL.
 * Every method validates live signed JWT and independently provisioned workforce
 * cell/facility/shift before OperationsStore applies PostgreSQL FORCE RLS.
 */
@RestController
@RequestMapping("/api/v1/operations/connectors")
public final class OperationsConnectorApi {
 private final WorkforceAdmission admission;
 private final OperationsConnectorService service;
 public OperationsConnectorApi(WorkforceAdmission admission,OperationsConnectorService service){
  this.admission=admission;this.service=service;
 }
 private OperationWorkflow.Actor actor(Jwt jwt){return admission.requireAuthoritativeGrant(jwt);}
 @PostMapping("/M21/procurement-exports/{itemId}")
 public OperationsConnectorService.ProcurementExport enqueueProcurement(
  @PathVariable UUID itemId,@AuthenticationPrincipal Jwt jwt){
  return service.enqueueProcurement(actor(jwt),itemId);
 }
 @GetMapping("/M21/procurement-exports")
 public List<OperationsConnectorService.ProcurementExport> pendingProcurement(
  @RequestParam(defaultValue="50")int limit,@AuthenticationPrincipal Jwt jwt){
  return service.pendingProcurement(actor(jwt),limit);
 }
 @GetMapping("/M22/barcode-stock")
 public OperationsConnectorService.BarcodeStock barcode(
  @RequestParam String sku,@RequestParam String lot,@RequestParam String bin,
  @AuthenticationPrincipal Jwt jwt){
  return service.resolveBarcode(actor(jwt),sku,lot,bin);
 }
 @PostMapping("/M25/vehicle-reservations")
 public OperationsConnectorService.VehicleReservation reserve(
  @RequestBody OperationsConnectorService.ReserveVehicleRequest request,
  @AuthenticationPrincipal Jwt jwt){
  return service.reserveVehicle(actor(jwt),request);
 }
 @PostMapping("/M25/vehicle-reservations/{reservationId}/cancel")
 public OperationsConnectorService.VehicleReservation cancel(
  @PathVariable UUID reservationId,@AuthenticationPrincipal Jwt jwt){
  return service.cancelReservation(actor(jwt),reservationId);
 }
 @GetMapping("/M29/publications")
 public OperationsConnectorService.PublicationPage publications(
  @RequestParam(required=false)String afterAt,@RequestParam(required=false)String afterId,
  @RequestParam(defaultValue="50")int limit,@AuthenticationPrincipal Jwt jwt){
  return service.publicationPage(actor(jwt),afterAt,afterId,limit);
 }
 @PostMapping("/M37/delivery-leases")
 public List<OperationsConnectorService.NotificationLease> lease(
  @RequestParam(defaultValue="10")int limit,@AuthenticationPrincipal Jwt jwt){
  return service.leaseNotifications(actor(jwt),limit);
 }
}
