package org.synqoria.fleet.domain;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable bootstrap cell-to-upstream allowlist, supplied only by the trusted
 * deployment configuration. Never populate from an HTTP Host, routing response,
 * client-supplied URL, or unverified service discovery event.
 *
 * Updating the cell fleet requires a new independently authenticated registry
 * snapshot and an atomic gateway configuration rollout; this registry does not
 * implement dynamic reconfiguration or dataplane epoch fencing.
 */
public final class GatewayCellDestinationRegistry
    implements GatewayRouteAdmission.CellDestinations {
 private final Map<UUID,URI> targets;

 public GatewayCellDestinationRegistry(Map<UUID,URI> configured, String trustedDnsSuffix) {
  Objects.requireNonNull(configured);
  if(configured.isEmpty() || configured.size()>10_000)
   throw new IllegalArgumentException("invalid_gateway_registry_size");
  if(trustedDnsSuffix==null ||
     !trustedDnsSuffix.matches("\\.[a-z0-9.-]{5,100}") ||
     trustedDnsSuffix.contains("..") || trustedDnsSuffix.endsWith("."))
   throw new IllegalArgumentException("invalid_gateway_registry_suffix");
  for(var entry:configured.entrySet()){
   UUID cell=entry.getKey();
   URI url=entry.getValue();
   if(cell==null || url==null || !url.isAbsolute() ||
      !"https".equals(url.getScheme()) || url.getHost()==null ||
      !url.getHost().endsWith(trustedDnsSuffix) ||
      url.getHost().length()<=trustedDnsSuffix.length() ||
      url.getRawUserInfo()!=null || url.getRawFragment()!=null ||
      url.getRawQuery()!=null ||
      !("".equals(url.getRawPath()) || "/".equals(url.getRawPath())) ||
      url.getPort()==0 || url.getPort()>65535)
    throw new IllegalArgumentException("invalid_gateway_registry_destination");
  }
  targets=Map.copyOf(configured);
 }
 @Override public URI resolve(UUID cellId) {
  URI target=targets.get(cellId);
  if(target==null)throw new IllegalStateException("cell_destination_not_configured");
  return target;
 }
 public int count(){return targets.size();}
}
