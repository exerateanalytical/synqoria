package org.synqoria.fleet.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway-side admission fence for M39 routing decisions. A gateway MUST call
 * admit before forwarding any request to a hospital cell. A signed decision is
 * not itself evidence that a caller may access a patient's data.
 *
 * The monotonic observations are per gateway process; production gateways MUST
 * also consult a shared authoritative directory/epoch source on cold start and
 * after reconnect. Never use this cache as the directory authority.
 */
public final class RoutingDecisionGate {
 private RoutingDecisionGate() {}

 public record Envelope(
  String routeKey, UUID cellId, long placementEpoch, long directoryVersion,
  Instant issuedAt, Instant expiresAt, String signature
 ) {
  public Envelope {
   Objects.requireNonNull(routeKey, "routeKey");
   Objects.requireNonNull(cellId, "cellId");
   Objects.requireNonNull(issuedAt, "issuedAt");
   Objects.requireNonNull(expiresAt, "expiresAt");
   Objects.requireNonNull(signature, "signature");
  }
  Domain.Decision decision() {
   return new Domain.Decision(routeKey, cellId, placementEpoch, directoryVersion, issuedAt, expiresAt);
  }
 }

 /** Authoritative minimum for this route, supplied by an independently authenticated directory. */
 public record Fence(long minimumEpoch, long minimumDirectoryVersion) {}

 /** Each request must pass through this interface; fail closed when it is unavailable. */
 @FunctionalInterface
 public interface DirectoryFence {
  Fence load(String routeKey) throws Exception;
 }

 public static final class Rejected extends RuntimeException {
  public Rejected(String code) { super(code, null, false, false); }
 }

 /**
  * Strong route binding, temporal validation and rollback protection.
  * It is essential that the key be securely configured and match the control
  * plane signing key; never fetch it from a browser/request header.
  */
 public static final class Verifier {
  private final String key;
  private final Clock clock;
  private final DirectoryFence directory;
  private final ConcurrentHashMap<String, Fence> observed = new ConcurrentHashMap<>();

  public Verifier(String key, Clock clock, DirectoryFence directory) {
   if (key == null || key.length() < 32) throw new IllegalArgumentException("routing_key_not_configured");
   this.key = key;
   this.clock = Objects.requireNonNull(clock);
   this.directory = Objects.requireNonNull(directory);
  }

  public UUID admit(String requestedRouteKey, Envelope envelope) {
   if (envelope == null || requestedRouteKey == null ||
       !requestedRouteKey.matches("[a-z0-9][a-z0-9.-]{2,119}") ||
       !requestedRouteKey.equals(envelope.routeKey())) throw new Rejected("route_binding_denied");
   Instant now = clock.instant();
   if (envelope.placementEpoch() < 0 || envelope.directoryVersion() < 0 ||
       envelope.issuedAt().isAfter(now.plusSeconds(5)) ||
       envelope.expiresAt().isAfter(envelope.issuedAt().plusSeconds(Domain.DECISION_TTL_SECONDS)) ||
       !envelope.expiresAt().isAfter(envelope.issuedAt()) ||
       !envelope.expiresAt().isAfter(now)) throw new Rejected("routing_decision_expired_or_invalid");

   if (!Domain.verify(key, envelope.decision(), envelope.signature(), now))
    throw new Rejected("routing_signature_denied");

   Fence authoritative;
   try {
    authoritative = directory.load(requestedRouteKey);
   } catch (Exception ex) {
    throw new Rejected("routing_directory_unavailable");
   }
   if (authoritative == null || authoritative.minimumEpoch() < 0 ||
       authoritative.minimumDirectoryVersion() < 0)
    throw new Rejected("routing_directory_unavailable");

   // Epoch/version floors can only increase, even if a stale directory replica responds.
   Fence highest = observed.compute(requestedRouteKey, (ignored, previous) ->
    new Fence(Math.max(authoritative.minimumEpoch(), previous == null ? 0 : previous.minimumEpoch()),
     Math.max(authoritative.minimumDirectoryVersion(),
      previous == null ? 0 : previous.minimumDirectoryVersion())));
   if (envelope.placementEpoch() < highest.minimumEpoch() ||
       envelope.directoryVersion() < highest.minimumDirectoryVersion())
    throw new Rejected("routing_decision_stale");
   return envelope.cellId();
  }
 }
}
