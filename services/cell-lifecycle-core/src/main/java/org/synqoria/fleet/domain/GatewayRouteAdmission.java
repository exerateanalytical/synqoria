package org.synqoria.fleet.domain;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.synqoria.fleet.domain.RoutingDecisionGate.Envelope;
import org.synqoria.fleet.domain.RoutingDecisionGate.Fence;
import org.synqoria.fleet.domain.RoutingDecisionGate.Rejected;
import org.synqoria.fleet.domain.RoutingDecisionGate.Verifier;

/**
 * M39 gateway admission integration with the independent live route fence.
 *
 * Both sources must be authenticated, fail closed and carry no user-controlled
 * destination URL. A result is a trusted cell destination selected by UUID,
 * never from Host or X-Forwarded-Host. The downstream gateway still enforces
 * OIDC, patient/encounter access, TLS identity and network isolation.
 */
public final class GatewayRouteAdmission {
 public record CurrentRoute(
  String routeKey, UUID cellId, long placementEpoch, long directoryVersion,
  String cellState, String health, Instant observedAt
 ) {}

 public record Target(UUID cellId, URI destination, long placementEpoch, long directoryVersion) {}

 @FunctionalInterface
 public interface SignedDecisions {
  Envelope fetch(String routeKey) throws Exception;
 }
 @FunctionalInterface
 public interface Directory {
  CurrentRoute snapshot(String routeKey) throws Exception;
 }
 @FunctionalInterface
 public interface CellDestinations {
  URI resolve(UUID cellId) throws Exception;
 }

 private final SignedDecisions signed;
 private final Directory directory;
 private final CellDestinations destinations;
 private final Clock clock;
 private final String trustedDnsSuffix;
 private final Verifier verifier;

 /**
  * The configured suffix is fixed at gateway startup, e.g. ".svc.cluster.local".
  * It must never be taken from a client-supplied Host header.
  */
 public GatewayRouteAdmission(
  String routingSigningKey, Clock clock, SignedDecisions signed,
  Directory directory, CellDestinations destinations, String trustedDnsSuffix
 ) {
  this.clock=Objects.requireNonNull(clock);
  this.signed=Objects.requireNonNull(signed);
  this.directory=Objects.requireNonNull(directory);
  this.destinations=Objects.requireNonNull(destinations);
  if(trustedDnsSuffix==null || !trustedDnsSuffix.matches("\\.[a-z0-9.-]{5,100}"))
   throw new IllegalArgumentException("invalid_trusted_destination_suffix");
  this.trustedDnsSuffix=trustedDnsSuffix;
  this.verifier=new Verifier(routingSigningKey,clock,routeKey->{
   CurrentRoute r=directory.snapshot(routeKey);
   if(r==null || !routeKey.equals(r.routeKey()) || r.cellId()==null)
    throw new IllegalStateException("missing_route");
   if(!Domain.routable(r.cellState(),r.health(),r.observedAt(),clock.instant()))
    throw new IllegalStateException("unhealthy_route");
   return new Fence(r.placementEpoch(),r.directoryVersion());
  });
 }

 /** Re-check the live directory after signature admission to avoid stale target swaps. */
 public Target admit(String requestedRouteKey) {
  // Reject untrusted route identifiers BEFORE any signed/control-plane HTTP lookup.
  if(requestedRouteKey==null || !requestedRouteKey.matches("[a-z0-9][a-z0-9.-]{2,119}"))
   throw new Rejected("route_binding_denied");
  try {
   Envelope signedDecision=signed.fetch(requestedRouteKey);
   UUID signedCell=verifier.admit(requestedRouteKey,signedDecision);
   CurrentRoute current=directory.snapshot(requestedRouteKey);
   if(current==null || !requestedRouteKey.equals(current.routeKey()) ||
      !signedCell.equals(current.cellId()) ||
      signedDecision.placementEpoch()!=current.placementEpoch() ||
      signedDecision.directoryVersion()!=current.directoryVersion())
    throw new Rejected("routing_target_mismatch");
   if(!Domain.routable(current.cellState(),current.health(),current.observedAt(),clock.instant()))
    throw new Rejected("routing_cell_not_healthy");

   URI uri=destinations.resolve(signedCell);
   if(uri==null || !"https".equals(uri.getScheme()) ||
      uri.getHost()==null || !uri.getHost().endsWith(trustedDnsSuffix) ||
      uri.getHost().length()<=trustedDnsSuffix.length() ||
      uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null ||
      !("".equals(uri.getRawPath()) || "/".equals(uri.getRawPath())) ||
      uri.getPort()==0 || uri.getPort()>65535)
    throw new Rejected("untrusted_cell_destination");
   return new Target(signedCell,uri,signedDecision.placementEpoch(),signedDecision.directoryVersion());
  } catch(Rejected e) {
   throw e;
  } catch(Exception e) {
   // Never silently fall back to a previous route or an arbitrary default cell.
   throw new Rejected("routing_admission_unavailable");
  }
 }
}
