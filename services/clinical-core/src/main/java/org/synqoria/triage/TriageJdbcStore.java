package org.synqoria.triage;

import javax.crypto.SecretKey;
import org.synqoria.security.TrustedAccessContext;
import org.synqoria.security.TrustedClinicalAccessEvidence;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Restricted M03 transactional adapter. No application grants or live DB attestation are implied. */
public final class TriageJdbcStore {
 private TriageJdbcStore(){}
 public static final String LOCK="SELECT revision,stage,station_id,assignee_id,priority_code,safety_hold,paused,signed_assessment,snapshot_ciphertext,snapshot_key_id,snapshot_digest FROM synqoria_triage_cases_quarantine WHERE tenant_id=? AND facility_id=? AND patient_id=? AND id=? FOR UPDATE";
 public static final String EVENT_TIP="SELECT revision,event_digest FROM synqoria_triage_events_quarantine WHERE tenant_id=? AND facility_id=? AND patient_id=? AND case_id=? ORDER BY revision DESC LIMIT 1";
 public static final String OUTBOX_LINK="SELECT event_digest FROM synqoria_triage_outbox_quarantine WHERE tenant_id=? AND facility_id=? AND patient_id=? AND case_id=? AND command_id=?";
 public static final String UPDATE="UPDATE synqoria_triage_cases_quarantine SET revision=?,stage=?,station_id=?,assignee_id=?,priority_code=?,safety_hold=?,paused=?,signed_assessment=?,snapshot_ciphertext=?,snapshot_key_id=?,snapshot_digest=?,updated_at=? WHERE tenant_id=? AND facility_id=? AND patient_id=? AND id=? AND revision=?";
 public static final String EVENT="INSERT INTO synqoria_triage_events_quarantine (tenant_id,facility_id,patient_id,case_id,command_id,actor_id,revision,event_type,previous_digest,state_digest,event_digest,occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
 public static final String OUTBOX="INSERT INTO synqoria_triage_outbox_quarantine (event_id,tenant_id,facility_id,patient_id,case_id,command_id,event_digest,occurred_at) VALUES (?,?,?,?,?,?,?,?)";
 @FunctionalInterface public interface Mutation {TriageCase apply(TriageCase current);}
 public static TriageCase mutate(Connection c,TrustedAccessContext identity,TrustedClinicalAccessEvidence evidence,
     String capability,TriageJdbcScope.Scope scope,String role,UUID caseId,TriageCommand command,
     String keyId,SecretKey key,Mutation mutation)throws SQLException{
  if(c==null||identity==null||evidence==null||capability==null||scope==null||caseId==null||command==null||keyId==null||key==null||mutation==null||!scope.actor().equals(command.actor())||!scope.actor().equals(identity.actorId())||!scope.tenant().equals(identity.tenantId())||!scope.facility().equals(identity.facilityId()))
   throw new SQLException("trusted triage command unavailable","42501");
  try{
   TriageAuthorization.require(identity,evidence,scope.tenant(),scope.facility(),scope.patient(),"triage:manage",command.trustedNow());
   TriageAuthorization.require(identity,evidence,scope.tenant(),scope.facility(),scope.patient(),capability,command.trustedNow());
   TriageJdbcFence.require(c,role);
   TriageJdbcScope.install(c,scope);
   TriageJdbcFence.budget(c);
   TriageCase before=lock(c,scope,caseId,key);
   if(before.revision()!=command.expectedRevision())throw new SQLException("stale triage revision","40001");
   verifyPersistedTip(c,scope,caseId,before);
   for(var event:before.changes())if(event.commandId().equals(command.commandId()))throw new SQLException("replayed triage command","23505");
   TriageCase after=mutation.apply(before);
   validate(before,after,scope,command);
   byte[] clear=TriageSnapshotCodec.encode(after);
   var seal=TriageEnvelopeCrypto.seal(new TriageEnvelopeCrypto.Scope(scope.tenant(),scope.facility(),scope.patient(),caseId,after.revision()),keyId,key,clear);
   update(c,scope,caseId,before.revision(),after,seal);
   TriageCase.Change event=after.changes().get(after.changes().size()-1);
   appendEvent(c,scope,caseId,event);
   appendOutbox(c,scope,caseId,event);
   c.commit();
   return after;
  }catch(SQLException|RuntimeException failure){try{c.rollback();}catch(SQLException suppressed){failure.addSuppressed(suppressed);}throw failure;}
 }
 private static TriageCase lock(Connection c,TriageJdbcScope.Scope scope,UUID id,SecretKey key)throws SQLException{
  try(PreparedStatement s=c.prepareStatement(LOCK)){
   bindScope(s,scope,id,1);
   try(ResultSet r=s.executeQuery()){
    if(!r.next())throw new SQLException("triage case unavailable","42501");
    int rev=r.getInt(1);String stage=r.getString(2);UUID station=(UUID)r.getObject(3),assignee=(UUID)r.getObject(4);String priority=r.getString(5);boolean held=r.getBoolean(6),paused=r.getBoolean(7),signed=r.getBoolean(8);byte[] cipher=r.getBytes(9);String keyId=r.getString(10),digest=r.getString(11);
    if(r.next()||rev<1||!TriageCase.hex(digest))throw new SQLException("triage row integrity unavailable","42501");
    byte[] clear=TriageEnvelopeCrypto.open(new TriageEnvelopeCrypto.Scope(scope.tenant(),scope.facility(),scope.patient(),id,rev),new TriageEnvelopeCrypto.Envelope(keyId,cipher),key);
    TriageCase aggregate=TriageSnapshotCodec.decode(clear);
    if(!scope.tenant().equals(aggregate.tenant())||!scope.facility().equals(aggregate.facility())||!scope.patient().equals(aggregate.patient())||!id.equals(aggregate.id())||rev!=aggregate.revision()||!digest.equals(TriageAudit.stateDigest(aggregate))||!aggregate.stage().name().equals(stage)||!Objects.equals(station,aggregate.station())||!Objects.equals(assignee,aggregate.assignee())||!Objects.equals(priority,aggregate.clinicianPriority())||held!=aggregate.safetyHold()||paused!=aggregate.paused()||signed!=aggregate.assessedSigned())
     throw new SQLException("triage snapshot mismatch","42501");
    return aggregate;
   }
  }
 }
 private static void verifyPersistedTip(Connection c,TriageJdbcScope.Scope scope,UUID id,TriageCase aggregate)throws SQLException{
  if(aggregate.changes().isEmpty())return;
  TriageCase.Change tip=aggregate.changes().get(aggregate.changes().size()-1);
  try(PreparedStatement s=c.prepareStatement(EVENT_TIP)){
   bindScope(s,scope,id,1);
   try(ResultSet r=s.executeQuery()){
    if(!r.next()||r.getLong(1)!=tip.revision()||!tip.digest().equals(r.getString(2))||r.next())throw new SQLException("triage audit tip unavailable","42501");
   }
  }
  try(PreparedStatement s=c.prepareStatement(OUTBOX_LINK)){
   bindScope(s,scope,id,1);s.setObject(5,tip.commandId());
   try(ResultSet r=s.executeQuery()){
    if(!r.next()||!tip.digest().equals(r.getString(1))||r.next())throw new SQLException("triage outbox tip unavailable","42501");
   }
  }
 }
 private static void validate(TriageCase before,TriageCase after,TriageJdbcScope.Scope scope,TriageCommand command)throws SQLException{
  if(after==null||!before.id().equals(after.id())||!scope.tenant().equals(after.tenant())||!scope.facility().equals(after.facility())||!scope.patient().equals(after.patient())||!Objects.equals(before.appointment(),after.appointment())||after.revision()!=before.revision()+1||after.changes().size()!=before.changes().size()+1||!TriageAudit.verify(after))throw new SQLException("invalid triage transition","42501");
  var last=after.changes().get(after.changes().size()-1);
  if(!last.commandId().equals(command.commandId())||!last.actor().equals(scope.actor())||!last.at().equals(command.trustedNow())||!after.changes().subList(0,before.changes().size()).equals(before.changes()))throw new SQLException("triage audit transition invalid","42501");
 }
 private static void update(Connection c,TriageJdbcScope.Scope scope,UUID id,int revision,TriageCase after,TriageEnvelopeCrypto.Envelope seal)throws SQLException{
  try(PreparedStatement s=c.prepareStatement(UPDATE)){
   s.setInt(1,after.revision());s.setString(2,after.stage().name());nullableUuid(s,3,after.station());nullableUuid(s,4,after.assignee());s.setString(5,after.clinicianPriority());s.setBoolean(6,after.safetyHold());s.setBoolean(7,after.paused());s.setBoolean(8,after.assessedSigned());s.setBytes(9,seal.ciphertext());s.setString(10,seal.keyId());s.setString(11,TriageAudit.stateDigest(after));s.setTimestamp(12,Timestamp.from(after.changes().get(after.changes().size()-1).at()));bindScope(s,scope,id,13);s.setInt(17,revision);
   if(s.executeUpdate()!=1)throw new SQLException("triage compare-and-swap failed","40001");
  }
 }
 private static void appendEvent(Connection c,TriageJdbcScope.Scope scope,UUID id,TriageCase.Change e)throws SQLException{
  try(PreparedStatement s=c.prepareStatement(EVENT)){
   bindScope(s,scope,id,1);s.setObject(5,e.commandId());s.setObject(6,e.actor());s.setLong(7,e.revision());s.setString(8,e.operation());s.setString(9,e.previousDigest());s.setString(10,e.stateDigest());s.setString(11,e.digest());s.setTimestamp(12,Timestamp.from(e.at()));if(s.executeUpdate()!=1)throw new SQLException("triage event append failed","40001");
  }
 }
 private static void appendOutbox(Connection c,TriageJdbcScope.Scope scope,UUID id,TriageCase.Change e)throws SQLException{
  try(PreparedStatement s=c.prepareStatement(OUTBOX)){
   s.setObject(1,UUID.randomUUID());bindScope(s,scope,id,2);s.setObject(6,e.commandId());s.setString(7,e.digest());s.setTimestamp(8,Timestamp.from(e.at()));if(s.executeUpdate()!=1)throw new SQLException("triage outbox append failed","40001");
  }
 }
 private static void bindScope(PreparedStatement s,TriageJdbcScope.Scope scope,UUID id,int index)throws SQLException{s.setObject(index,scope.tenant());s.setObject(index+1,scope.facility());s.setObject(index+2,scope.patient());s.setObject(index+3,id);}
 private static void nullableUuid(PreparedStatement s,int i,UUID id)throws SQLException{if(id==null)s.setNull(i,Types.OTHER);else s.setObject(i,id);}
}
