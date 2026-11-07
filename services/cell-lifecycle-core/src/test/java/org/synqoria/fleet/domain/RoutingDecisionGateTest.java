package org.synqoria.fleet.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.synqoria.fleet.domain.RoutingDecisionGate.*;

class RoutingDecisionGateTest {
 private static final String KEY = "test-only-not-for-production-signing-key-123456789";
 private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
 private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
 private static final String ROUTE = "hospital-a.example";
 private final UUID cell = UUID.fromString("00000000-0000-0000-0000-000000000101");

 private Envelope signed(long epoch, long directoryVersion, Instant issued, Instant expires) {
  Domain.Decision d = new Domain.Decision(ROUTE, cell, epoch, directoryVersion, issued, expires);
  return new Envelope(ROUTE, cell, epoch, directoryVersion, issued, expires, Domain.sign(KEY, d));
 }
 private static void denied(String code, Runnable r) {
  Rejected error = assertThrows(Rejected.class, r::run);
  assertEquals(code, error.getMessage());
 }
 @Test void validDecisionAdmittedWhenFreshAndAuthoritative() {
  Verifier v = new Verifier(KEY, CLOCK, r -> new Fence(5, 7));
  assertEquals(cell, v.admit(ROUTE, signed(5, 7, NOW.minusSeconds(2), NOW.plusSeconds(58))));
 }
 @Test void deniesTamperingAndRouteConfusion() {
  Verifier v = new Verifier(KEY, CLOCK, r -> new Fence(0, 0));
  Envelope e = signed(5, 7, NOW, NOW.plusSeconds(60));
  denied("route_binding_denied", () -> v.admit("hospital-b.example", e));
  Envelope tampered = new Envelope(e.routeKey(), e.cellId(), 6, e.directoryVersion(), e.issuedAt(), e.expiresAt(), e.signature());
  denied("routing_signature_denied", () -> v.admit(ROUTE, tampered));
 }
 @Test void refusesExpiredOverlongAndFutureDecisions() {
  Verifier v = new Verifier(KEY, CLOCK, r -> new Fence(0, 0));
  denied("routing_decision_expired_or_invalid", () ->
   v.admit(ROUTE, signed(1, 1, NOW.minusSeconds(61), NOW.minusSeconds(1))));
  denied("routing_decision_expired_or_invalid", () ->
   v.admit(ROUTE, signed(1, 1, NOW, NOW.plusSeconds(61))));
  denied("routing_decision_expired_or_invalid", () ->
   v.admit(ROUTE, signed(1, 1, NOW.plusSeconds(6), NOW.plusSeconds(60))));
 }
 @Test void rollbackOfEpochOrDirectoryVersionDenied() {
  AtomicReference<Fence> floor = new AtomicReference<>(new Fence(3, 3));
  Verifier v = new Verifier(KEY, CLOCK, r -> floor.get());
  assertEquals(cell, v.admit(ROUTE, signed(3, 3, NOW, NOW.plusSeconds(60))));
  floor.set(new Fence(4, 4));
  assertEquals(cell, v.admit(ROUTE, signed(4, 4, NOW, NOW.plusSeconds(60))));
  floor.set(new Fence(1, 1)); // stale directory replica must not lower the observed fence
  denied("routing_decision_stale", () -> v.admit(ROUTE, signed(3, 3, NOW, NOW.plusSeconds(60))));
  denied("routing_decision_stale", () -> v.admit(ROUTE, signed(4, 3, NOW, NOW.plusSeconds(60))));
 }
 @Test void directoryUnavailableAlwaysFailsClosed() {
  Verifier v = new Verifier(KEY, CLOCK, r -> { throw new IllegalStateException("network unavailable"); });
  denied("routing_directory_unavailable", () -> v.admit(ROUTE, signed(5, 5, NOW, NOW.plusSeconds(60))));
  Verifier empty = new Verifier(KEY, CLOCK, r -> null);
  denied("routing_directory_unavailable", () -> empty.admit(ROUTE, signed(5, 5, NOW, NOW.plusSeconds(60))));
 }
 @Test void invalidSigningKeyRejectedAtStartup() {
  assertThrows(IllegalArgumentException.class, () -> new Verifier("short", CLOCK, r -> new Fence(0,0)));
 }
}
