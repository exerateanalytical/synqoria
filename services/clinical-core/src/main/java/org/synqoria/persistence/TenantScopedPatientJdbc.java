package org.synqoria.persistence;
import org.synqoria.security.*;
import java.sql.*;
import java.util.*;
/** P04-B0008: tenant + facility bound JDBC reader. Requires a real connection pool and PostgreSQL RLS tests before use. */
public final class TenantScopedPatientJdbc implements PatientReadService.PatientRepository {
 @FunctionalInterface public interface Connections {Connection open() throws SQLException;}
 private final Connections pool;private final UUID facility;
 private static final String SQL="SELECT tenant_id, home_facility_id AS facility_id, patient_id, lower(identity_status) AS identity_status, revision FROM patient_master_index WHERE tenant_id = ? AND home_facility_id = ? AND patient_id = ?";
 public TenantScopedPatientJdbc(Connections pool,UUID facility){this.pool=Objects.requireNonNull(pool);this.facility=Objects.requireNonNull(facility);}
 public static String parameterizedSql(){return SQL;}
 @Override public Optional<PatientRecordEnvelope> getForTenant(UUID tenant,UUID patient){
  if(tenant==null||patient==null)return Optional.empty();
  try(Connection c=pool.open();PreparedStatement q=c.prepareStatement(SQL)){
   if(c.isReadOnly()==false)throw new SQLException("Non-read-only pool");
   q.setObject(1,tenant);q.setObject(2,facility);q.setObject(3,patient);
   q.setQueryTimeout(3);q.setMaxRows(2);
   try(ResultSet rs=q.executeQuery()){
    if(!rs.next())return Optional.empty();
    var out=new PatientRecordEnvelope((UUID)rs.getObject("tenant_id"),(UUID)rs.getObject("facility_id"),(UUID)rs.getObject("patient_id"),rs.getString("identity_status"),rs.getLong("revision"));
    if(rs.next()||!out.tenantId().equals(tenant)||!out.facilityId().equals(facility)||!out.patientId().equals(patient))throw new SQLException("Scope/uniqueness error");
    return Optional.of(out);
   }
  }catch(SQLException|IllegalArgumentException e){throw new IllegalStateException("Patient store unavailable");}
 }
}
