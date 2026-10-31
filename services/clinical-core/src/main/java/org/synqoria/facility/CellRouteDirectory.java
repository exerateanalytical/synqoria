package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;
import java.net.URI;import java.util.*;
/**
 * P04-B0003 cell routing reference: trusted bootstrap endpoints, never caller
 * headers or patient URL. No network/TLS/DNS or service discovery is performed.
 */
public final class CellRouteDirectory {
 public record Cell(UUID id,URI endpoint,String status,long revision) {
  public Cell {
   Objects.requireNonNull(id);Objects.requireNonNull(endpoint);
   if(revision<1||!Set.of("ACTIVE","SUSPENDED","RETIRED").contains(status))throw new IllegalArgumentException("Invalid cell");
   var host=endpoint.getHost();
   if(!"https".equals(endpoint.getScheme())||host==null||!host.matches("[a-zA-Z][a-zA-Z0-9-]*(\\.[a-zA-Z][a-zA-Z0-9-]*)+")
      ||endpoint.getUserInfo()!=null||endpoint.getRawQuery()!=null||endpoint.getRawFragment()!=null
      ||!"".equals(endpoint.getRawPath())&&!"/".equals(endpoint.getRawPath())
      ||(endpoint.getPort()!=-1&&endpoint.getPort()!=443))throw new IllegalArgumentException("Untrusted cell endpoint");
  }
 }
 public record Tenant(UUID tenant,UUID cell,boolean active) {
  public Tenant{Objects.requireNonNull(tenant);Objects.requireNonNull(cell);}
 }
 private final Map<UUID,Cell> cells;private final Map<UUID,Tenant> tenants;private final FacilityDirectory facilities;
 public CellRouteDirectory(Collection<Cell> cells,Collection<Tenant> tenants,FacilityDirectory facilities) {
  this.cells=unique(cells,Cell::id);this.tenants=unique(tenants,Tenant::tenant);this.facilities=Objects.requireNonNull(facilities);
  for(var t:this.tenants.values())if(!this.cells.containsKey(t.cell()))throw new IllegalArgumentException("Tenant cell not registered");
 }
 private static <T> Map<UUID,T> unique(Collection<T> values,java.util.function.Function<T,UUID> key) {
  Objects.requireNonNull(values);var map=new HashMap<UUID,T>();
  for(var v:values)if(map.putIfAbsent(key.apply(Objects.requireNonNull(v)),v)!=null)throw new IllegalArgumentException("Duplicate routing identity");
  return Map.copyOf(map);
 }
 public Optional<URI> route(TrustedAccessContext trusted) {
  if(trusted==null)return Optional.empty();
  var tenant=tenants.get(trusted.tenantId());if(tenant==null||!tenant.active())return Optional.empty();
  var cell=cells.get(tenant.cell());
  if(cell==null||!"ACTIVE".equals(cell.status())||!facilities.permitsActiveFacility(trusted,cell.id()))return Optional.empty();
  return Optional.of(cell.endpoint());
 }
}
