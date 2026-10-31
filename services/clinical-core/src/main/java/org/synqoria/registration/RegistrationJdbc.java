package org.synqoria.registration;
import org.synqoria.security.TrustedAccessContext;
import java.sql.*;import java.time.*;import java.util.*;
/** P05-B0005/P05-B0006. One explicit DB transaction: patient, demographics, audit, outbox and command claim. TEST seam, not deployed. */
final class RegistrationJdbc {
 @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
 public enum State { COMMITTED, REJECTED, UNAVAILABLE }
 public record Result(State state, UUID patientId){public Result{if((state==State.COMMITTED)!=(patientId!=null))throw new IllegalArgumentException("State mismatch");}}
 private final Connections connections;
 public RegistrationJdbc(Connections connections){this.connections=Objects.requireNonNull(connections);}
 private static int exec(Connection c,String sql,Object... binds) throws SQLException {
  try(PreparedStatement s=c.prepareStatement(sql)){
   s.setQueryTimeout(5);
   for(int i=0;i<binds.length;i++){
    Object b=binds[i];if(b instanceof Instant instant)s.setTimestamp(i+1,Timestamp.from(instant));
    else if(b instanceof LocalDate date)s.setDate(i+1,java.sql.Date.valueOf(date));
    else if(b instanceof byte[] raw)s.setBytes(i+1,raw);
    else s.setObject(i+1,b);
   }
   return s.executeUpdate();
  }
 }
 public Result create(TrustedAccessContext actor,RegistrationCommand command,boolean activeFacility,
   boolean validShift,boolean currentGrants,boolean unresolvedDuplicate,Instant now) {
  if(!RegistrationRequestPolicy.allowed(actor,command,activeFacility,validShift,currentGrants,unresolvedDuplicate,false,now))return new Result(State.REJECTED,null);
  try(Connection c=connections.open()){
   if(c.getAutoCommit()||c.isReadOnly())return new Result(State.UNAVAILABLE,null);
   try{
    UUID t=actor.tenantId(),f=actor.facilityId(),p=command.patientId(),id=command.commandId(),corr=command.correlationId();
    RegistrationDraft d=command.draft();
    String patientSql="INSERT INTO patient_master_index (tenant_id,patient_id,home_facility_id,identity_status,revision) VALUES (?,?,?,'PROVISIONAL',1)";
    String demographicsSql="INSERT INTO patient_registration_demographics (tenant_id,patient_id,facility_id,legal_name,date_of_birth,estimated_age_years,birth_information_status,preferred_language,recorded_at) VALUES (?,?,?,?,?,?,?,?,?)";
    String auditSql="INSERT INTO patient_registration_audit (tenant_id,facility_id,event_id,actor_id,patient_id,correlation_id,recorded_at,operation) VALUES (?,?,?,?,?,?,?,'patient:register:provisional')";
    String outboxSql="INSERT INTO clinical_outbox (tenant_id,facility_id,event_id,aggregate_type,aggregate_id,aggregate_revision,event_type,schema_version,correlation_id,payload) VALUES (?,?,?,'patient',?,1,'patient.registered.provisional',1,?,'{}'::jsonb)";
    String claimSql="INSERT INTO patient_registration_command_claim (tenant_id,facility_id,command_id,patient_id,fingerprint_hmac) VALUES (?,?,?,?,?)";
    if(exec(c,patientSql,t,p,f)!=1)throw new SQLException("patient insert failed");
    if(exec(c,demographicsSql,t,p,f,d.legalName(),d.dateOfBirth(),d.estimatedAgeYears(),d.birthInformation().name(),d.language(),now)!=1)throw new SQLException("demographics insert failed");
    if(exec(c,auditSql,t,f,UUID.randomUUID(),actor.actorId(),p,corr,now)!=1)throw new SQLException("audit failed");
    if(exec(c,outboxSql,t,f,UUID.randomUUID(),p,corr)!=1)throw new SQLException("outbox failed");
    if(exec(c,claimSql,t,f,id,p,command.fingerprintHmac())!=1)throw new SQLException("claim failed");
    c.commit();return new Result(State.COMMITTED,p);
   }catch(Exception e){try{c.rollback();}catch(SQLException ignored){}return new Result(State.UNAVAILABLE,null);}
  }catch(Exception e){return new Result(State.UNAVAILABLE,null);}
 }
}
