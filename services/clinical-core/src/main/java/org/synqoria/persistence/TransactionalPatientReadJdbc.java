package org.synqoria.persistence;
import org.synqoria.security.*;
import java.sql.*;import java.time.Instant;import java.util.*;
/** P01-B0023. One database transaction for authoritative patient row + audit intent.
 * An external approved proof resolver must authorize; this is NOT production OIDC/RLS.
 * The read is not released until the insert and COMMIT both succeed.
 */
public final class TransactionalPatientReadJdbc {
 @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
 @FunctionalInterface public interface ApprovedPolicy {boolean mayRead(TrustedAccessContext actor,PatientRecordEnvelope patient,Instant now);}
 public enum Status { OK, NOT_DISCLOSED, UNAVAILABLE }
 public record Result(Status status,PatientRecordEnvelope patient) {
  public Result {if((status==Status.OK)!=(patient!=null))throw new IllegalArgumentException("Result integrity");}
 }
 static final String SQL="SELECT tenant_id, home_facility_id AS facility_id, patient_id, lower(identity_status) AS identity_status, revision FROM patient_master_index WHERE tenant_id = ? AND home_facility_id = ? AND patient_id = ?";
 private final Connections pool;private final ApprovedPolicy policy;
 public TransactionalPatientReadJdbc(Connections pool,ApprovedPolicy policy){this.pool=Objects.requireNonNull(pool);this.policy=Objects.requireNonNull(policy);}
 public Result read(TrustedAccessContext caller,UUID patientId,UUID correlationId,Instant now){
  if(caller==null||patientId==null||correlationId==null||now==null
    ||!PatientScopePolicy.canReadPatient(caller,caller.tenantId(),caller.facilityId(),patientId))return new Result(Status.NOT_DISCLOSED,null);
  try(Connection c=pool.open()){
   if(c.getAutoCommit()||c.isReadOnly())throw new SQLException("Explicit writable transaction required");
   try {
    PatientRecordEnvelope p;
    try(PreparedStatement q=c.prepareStatement(SQL)){
     q.setObject(1,caller.tenantId());q.setObject(2,caller.facilityId());q.setObject(3,patientId);
     q.setMaxRows(2);q.setQueryTimeout(3);
     try(ResultSet rs=q.executeQuery()){
      if(!rs.next())return rolledBack(c,Status.NOT_DISCLOSED);
      p=new PatientRecordEnvelope((UUID)rs.getObject("tenant_id"),(UUID)rs.getObject("facility_id"),(UUID)rs.getObject("patient_id"),rs.getString("identity_status"),rs.getLong("revision"));
      if(rs.next()||!PatientScopePolicy.canReadPatient(caller,p.tenantId(),p.facilityId(),p.patientId())||!p.patientId().equals(patientId))return rolledBack(c,Status.NOT_DISCLOSED);
     }
    }
    if(!policy.mayRead(caller,p,now))return rolledBack(c,Status.NOT_DISCLOSED);
    AuditWriteJdbc.insert(c,new ClinicalAuditEntry(UUID.randomUUID(),correlationId,caller.actorId(),caller.tenantId(),caller.facilityId(),patientId,"patient:read",now));
    c.commit();return new Result(Status.OK,p);
   }catch(Exception e){try{c.rollback();}catch(SQLException suppressed){e.addSuppressed(suppressed);}return new Result(Status.UNAVAILABLE,null);}
  }catch(Exception e){return new Result(Status.UNAVAILABLE,null);}
 }
 private static Result rolledBack(Connection c,Status status) throws SQLException {c.rollback();return new Result(status,null);}
}
