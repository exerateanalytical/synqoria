package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;
import java.sql.*;import java.util.*;
/** Reads facility/current cell from DB at request time, not a stale or client-controlled local snapshot. */
public final class JdbcFacilityGate {
 @FunctionalInterface public interface Connections {Connection open() throws SQLException;}
 public static final String SQL="SELECT f.tenant_id,f.cell_id,f.facility_id,f.facility_status,f.revision FROM hospital_facilities f JOIN hospital_tenants t ON (f.tenant_id=t.tenant_id AND f.cell_id=t.cell_id) JOIN hospital_cells c ON (t.cell_id=c.cell_id) WHERE f.tenant_id=? AND f.facility_id=? AND f.cell_id=? AND f.facility_status='ACTIVE' AND t.tenant_status='ACTIVE' AND c.cell_status='ACTIVE'";
 private final Connections pool;private final UUID cell;
 public JdbcFacilityGate(Connections pool,UUID cell){this.pool=Objects.requireNonNull(pool);this.cell=Objects.requireNonNull(cell);}
 public Optional<FacilityBinding> resolve(TrustedAccessContext actor){
  if(actor==null)return Optional.empty();
  try(Connection c=pool.open();PreparedStatement p=c.prepareStatement(SQL)){
   p.setObject(1,actor.tenantId());p.setObject(2,actor.facilityId());p.setObject(3,cell);p.setMaxRows(2);p.setQueryTimeout(3);
   try(ResultSet r=p.executeQuery()){
    if(!r.next())return Optional.empty();
    var value=new FacilityBinding((UUID)r.getObject("cell_id"),(UUID)r.getObject("tenant_id"),(UUID)r.getObject("facility_id"),r.getString("facility_status"),r.getLong("revision"));
    if(r.next()||!"ACTIVE".equals(value.status())||!actor.tenantId().equals(value.tenantId())||!actor.facilityId().equals(value.facilityId())||!cell.equals(value.cellId()))return Optional.empty();
    return Optional.of(value);
   }
  }catch(Exception e){return Optional.empty();}
 }
}
