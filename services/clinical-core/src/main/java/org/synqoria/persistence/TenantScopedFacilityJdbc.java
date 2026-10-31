package org.synqoria.persistence;
import org.synqoria.facility.FacilityBinding;
import java.sql.*;
import java.util.*;
/** P04-B0009: bounded tenant/cell/facility lookup; no implicit hospital directory enumeration. */
public final class TenantScopedFacilityJdbc {
 private final TenantScopedPatientJdbc.Connections connections;
 public TenantScopedFacilityJdbc(TenantScopedPatientJdbc.Connections c){connections=Objects.requireNonNull(c);}
 private static final String SQL="SELECT cell_id, tenant_id, facility_id, facility_status AS status, revision FROM hospital_facilities WHERE tenant_id = ? AND cell_id = ? AND facility_id = ?";
 public static String parameterizedSql(){return SQL;}
 public Optional<FacilityBinding> resolve(UUID tenant,UUID cell,UUID facility){
  if(tenant==null||cell==null||facility==null)return Optional.empty();
  try(Connection c=connections.open();PreparedStatement q=c.prepareStatement(SQL)){
   if(!c.isReadOnly())throw new SQLException("Non-read-only connection");
   q.setObject(1,tenant);q.setObject(2,cell);q.setObject(3,facility);q.setMaxRows(2);q.setQueryTimeout(3);
   try(ResultSet rs=q.executeQuery()){
    if(!rs.next())return Optional.empty();
    var value=new FacilityBinding((UUID)rs.getObject("cell_id"),(UUID)rs.getObject("tenant_id"),(UUID)rs.getObject("facility_id"),rs.getString("status"),rs.getLong("revision"));
    if(rs.next()||!value.tenantId().equals(tenant)||!value.cellId().equals(cell)||!value.facilityId().equals(facility))throw new SQLException("Scope/uniqueness failure");
    return "ACTIVE".equals(value.status())?Optional.of(value):Optional.empty();
   }
  }catch(SQLException|IllegalArgumentException e){throw new IllegalStateException("Facility store unavailable");}
 }
}
