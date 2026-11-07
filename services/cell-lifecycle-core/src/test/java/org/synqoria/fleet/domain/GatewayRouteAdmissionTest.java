package org.synqoria.fleet.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.synqoria.fleet.domain.GatewayRouteAdmission.*;
import org.synqoria.fleet.domain.RoutingDecisionGate.Envelope;
import org.synqoria.fleet.domain.RoutingDecisionGate.Rejected;

class GatewayRouteAdmissionTest {
 private static final String KEY="synthetic-only-hmac-key-never-use-in-production-12345";
 private static final String ROUTE="hospital-one.example";
 private static final Instant NOW=Instant.parse("2026-11-07T12:00:00Z");
 private static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
 private static final UUID A=UUID.fromString("00000000-0000-4000-8000-000000000011");
 private static final UUID B=UUID.fromString("00000000-0000-4000-8000-000000000022");
 private final AtomicReference<CurrentRoute> authoritative=new AtomicReference<>(
  new CurrentRoute(ROUTE,A,12,20,"ACTIVE","HEALTHY",NOW.minusSeconds(20)));
 private final AtomicReference<UUID> signedCell=new AtomicReference<>(A);
 private final AtomicReference<Long> signedEpoch=new AtomicReference<>(12L);
 private final AtomicReference<Long> signedDirectoryVersion=new AtomicReference<>(20L);
 private final AtomicReference<URI> destination=new AtomicReference<>(URI.create("https://a.cell.svc.cluster.local:8443/"));

 private Envelope signed(String routeKey)throws Exception{
  var d=new Domain.Decision(routeKey,signedCell.get(),signedEpoch.get(),signedDirectoryVersion.get(),
   NOW.minusSeconds(2),NOW.plusSeconds(58));
  return new Envelope(d.routeKey(),d.cellId(),d.placementEpoch(),d.directoryVersion(),
   d.issuedAt(),d.expiresAt(),Domain.sign(KEY,d));
 }
 private GatewayRouteAdmission gateway(){
  return new GatewayRouteAdmission(KEY,CLOCK,this::signed,route->authoritative.get(),
   cell->destination.get(),".svc.cluster.local");
 }
 private static void rejects(String code,Runnable block){
  var e=assertThrows(Rejected.class,block::run);
  assertEquals(code,e.getMessage());
 }

 @Test void admitsValidSignedCurrentHealthyRouteAndTrustedCell(){
  Target target=gateway().admit(ROUTE);
  assertEquals(A,target.cellId());
  assertEquals("a.cell.svc.cluster.local",target.destination().getHost());
  assertEquals(12,target.placementEpoch());
  assertEquals(20,target.directoryVersion());
 }
 @Test void rejectsTamperedOrOldTargetCellEvenIfEpochUnchanged(){
  var gateway=gateway();
  assertEquals(A,gateway.admit(ROUTE).cellId());
  authoritative.set(new CurrentRoute(ROUTE,B,12,20,"ACTIVE","HEALTHY",NOW));
  rejects("routing_target_mismatch",()->gateway.admit(ROUTE));
 }
 @Test void rejectsSupersededEpochAndDirectoryVersion(){
  var gateway=gateway();
  authoritative.set(new CurrentRoute(ROUTE,B,13,21,"ACTIVE","HEALTHY",NOW));
  rejects("routing_decision_stale",()->gateway.admit(ROUTE));
  signedCell.set(B);signedEpoch.set(13L);signedDirectoryVersion.set(21L);
  assertEquals(B,gateway.admit(ROUTE).cellId());
  authoritative.set(new CurrentRoute(ROUTE,A,12,20,"ACTIVE","HEALTHY",NOW));
  signedCell.set(A);signedEpoch.set(12L);signedDirectoryVersion.set(20L);
  rejects("routing_decision_stale",()->gateway.admit(ROUTE));
 }
 @Test void deniesDifferentHospitalRouteConfusion(){
  rejects("routing_directory_unavailable",()->gateway().admit("hospital-two.example"));
 }
 @Test void unhealthyMissingOrStaleDirectoryFailsClosed(){
  for(var bad:new CurrentRoute[]{
   new CurrentRoute(ROUTE,A,12,20,"FENCED","HEALTHY",NOW),
   new CurrentRoute(ROUTE,A,12,20,"ACTIVE","UNHEALTHY",NOW),
   new CurrentRoute(ROUTE,A,12,20,"ACTIVE","HEALTHY",NOW.minusSeconds(301)),
   null
  }){
   authoritative.set(bad);
   rejects("routing_directory_unavailable",()->gateway().admit(ROUTE));
  }
 }
 @Test void preventsUntrustedOutboundDestinations(){
  var gateway=gateway();
  for(var uri:new String[]{
   "http://a.cell.svc.cluster.local/",
   "https://a.cell.svc.cluster.local.evil.example/",
   "https://a.cell.svc.cluster.local/sneaky",
   "https://a.cell.svc.cluster.local/?to=b",
   "https://username@a.cell.svc.cluster.local/",
   "https://127.0.0.1/",
   "https://a.cell.svc.cluster.local/#extra"
  }){
   destination.set(URI.create(uri));
   rejects("untrusted_cell_destination",()->gateway.admit(ROUTE));
  }
 }
 @Test void missingControlPlaneOrRegistryFailsWithoutFallback(){
  var badDecision=new GatewayRouteAdmission(KEY,CLOCK,
    route->{throw new IllegalStateException("offline");},r->authoritative.get(),
    cell->destination.get(),".svc.cluster.local");
  rejects("routing_admission_unavailable",()->badDecision.admit(ROUTE));
  var badRegistry=new GatewayRouteAdmission(KEY,CLOCK,
    this::signed,r->authoritative.get(),
    cell->{throw new IllegalStateException("registry offline");},".svc.cluster.local");
  rejects("routing_admission_unavailable",()->badRegistry.admit(ROUTE));
 }
 @Test void refusesUnconfiguredTrustSuffix(){
  assertThrows(IllegalArgumentException.class,()->new GatewayRouteAdmission(KEY,CLOCK,
   this::signed,r->authoritative.get(),cell->destination.get(),""));
 }
}
