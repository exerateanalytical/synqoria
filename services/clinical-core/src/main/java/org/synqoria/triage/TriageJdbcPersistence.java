package org.synqoria.triage;

import javax.crypto.SecretKey;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Function;

/** JDBC implementation against *quarantined* V016 schema. No deployment claims: DB roles intentionally have no grants. */
public final class TriageJdbcPersistence implements TriagePersistencePort {
 public interface KeyResolver { SecretKey activeKey(); SecretKey resolve(String keyId); String activeId(); }
 private final DataSource source;
 private final KeyResolver keys;
 public TriageJdbcPersistence(DataSource source,KeyResolver keys){this.source=Objects.requireNonNull(source);this.keys=Objects.requireNonNull(keys);}
 @Override public <T> T transaction(Function<Unit,T> work){
  try(Connection c=source.getConnection()){
   if(!c.getAutoCommit())throw new SQLException("connection must not have an active unmanaged transaction");
   c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
   try(var timeout=c.createStatement()){
    timeout.execute("SET LOCAL lock_timeout = '3000ms'");
    timeout.execute("SET LOCAL statement_timeout = '10000ms'");
   }
   try{T result=work.apply(new JdbcUnit(c));c.commit();return result;}
   catch(RuntimeException | Error failure){try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}throw failure;}
   finally{try{c.setAutoCommit(true);}catch(SQLException ignored){ /* Connection closes; never reuse failed transaction. */ }}
  }catch(SQLException e){throw new IllegalStateException("triage transaction unavailable",e);}
 }
 private static void scope(PreparedStatement s,TriagePersistencePort.Scope sc,int start)throws SQLException{
  s.setObject(start,sc.tenant());s.setObject(start+1,sc.facility());s.setObject(start+2,sc.patient());s.setObject(start+3,sc.caseId());
 }
 private static TriagePersistencePort.Scope of(TriageCase c){return new TriagePersistencePort.Scope(c.tenant(),c.facility(),c.patient(),c.id());}
 private static void updated(PreparedStatement ps,TriageCase c,TriageEnvelopeCrypto.Envelope envelope,int index)throws SQLException{
  ps.setString(index,envelope.keyId());ps.setBytes(index+1,envelope.ciphertext());ps.setString(index+2,TriageAudit.stateDigest(c));ps.setObject(index+3,java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
 }
 private final class JdbcUnit implements Unit {
  private final Connection db;
  JdbcUnit(Connection db){this.db=db;}
  private TriageEnvelopeCrypto.Envelope encrypt(TriageCase c){return TriageEnvelopeCrypto.seal(new TriageEnvelopeCrypto.Scope(c.tenant(),c.facility(),c.patient(),c.id(),c.revision()),keys.activeId(),keys.activeKey(),TriageSnapshotCodec.encode(c));}
  @Override public TriageCase loadLocked(TriagePersistencePort.Scope sc){
   String sql="SELECT revision,encryption_key_id,encrypted_snapshot,state_digest FROM synqoria_triage_secure_cases_quarantine WHERE tenant_id=? AND facility_id=? AND patient_id=? AND case_id=? FOR UPDATE";
   try(var ps=db.prepareStatement(sql)){
    ps.setQueryTimeout(10);scope(ps,sc,1);
    try(var result=ps.executeQuery()){
     if(!result.next())return null;
     int revision=result.getInt(1);String keyId=result.getString(2);byte[] ciphertext=result.getBytes(3);String state=result.getString(4);
     byte[] raw=TriageEnvelopeCrypto.open(new TriageEnvelopeCrypto.Scope(sc.tenant(),sc.facility(),sc.patient(),sc.caseId(),revision),new TriageEnvelopeCrypto.Envelope(keyId,ciphertext),keys.resolve(keyId));
     TriageCase c;try{c=TriageSnapshotCodec.decode(raw);}finally{Arrays.fill(raw,(byte)0);}
     if(!sc.equals(of(c))||revision!=c.revision()||!TriageAudit.stateDigest(c).equals(state))throw new SecurityException("triage record scope/integrity unavailable");
     if(result.next())throw new SecurityException("ambiguous triage record");
     verifyLedger(db,sc,c);
     return c;
    }
   }catch(SQLException e){throw new IllegalStateException("scoped triage read failed",e);}
  }
  /** Every protected event MUST have matching receipt/outbox evidence; the first is revision-one arrival. */
  private void verifyLedger(Connection connection,TriagePersistencePort.Scope sc,TriageCase c){
   String sql="SELECT e.revision,e.command_id,e.operation,e.previous_digest,e.state_digest,e.event_digest,e.actor_digest,e.occurred_at,"+
      "r.event_digest,o.event_digest FROM synqoria_triage_secure_events_quarantine e "+
      "LEFT JOIN synqoria_triage_secure_receipts_quarantine r ON r.tenant_id=e.tenant_id AND r.facility_id=e.facility_id "+
      "AND r.patient_id=e.patient_id AND r.case_id=e.case_id AND r.command_id=e.command_id AND r.revision=e.revision "+
      "LEFT JOIN synqoria_triage_secure_outbox_quarantine o ON o.tenant_id=e.tenant_id AND o.facility_id=e.facility_id "+
      "AND o.patient_id=e.patient_id AND o.case_id=e.case_id AND o.command_id=e.command_id "+
      "WHERE e.tenant_id=? AND e.facility_id=? AND e.patient_id=? AND e.case_id=? ORDER BY e.revision";
   try(var ps=connection.prepareStatement(sql)){
    ps.setQueryTimeout(10);scope(ps,sc,1);
    var events=new ArrayList<TriageAuditEvidence.Event>();
    try(var rs=ps.executeQuery()){
     while(rs.next()){
      if(events.size()>=30000)throw new SecurityException("triage evidence exceeds supported bound");
      Timestamp timestamp=rs.getTimestamp(8);
      if(timestamp==null)throw new SecurityException("triage event time is missing");
      events.add(new TriageAuditEvidence.Event(rs.getInt(1),(UUID)rs.getObject(2),rs.getString(3),
        rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),timestamp.toInstant(),rs.getString(9),rs.getString(10)));
     }
    }
    TriageAuditEvidence.requireFullChain(c,events);
   }catch(SQLException|IllegalArgumentException ex){throw new SecurityException("triage ledger verification unavailable",ex);}
  }
  @Override public Receipt receipt(TriagePersistencePort.Scope sc,UUID command){
   String sql="SELECT request_digest,revision,event_digest,committed_at FROM synqoria_triage_secure_receipts_quarantine WHERE tenant_id=? AND facility_id=? AND patient_id=? AND case_id=? AND command_id=?";
   try(var ps=db.prepareStatement(sql)){ps.setQueryTimeout(10);scope(ps,sc,1);ps.setObject(5,command);
    try(var rs=ps.executeQuery()){if(!rs.next())return null;var r=new Receipt(command,rs.getString(1),rs.getInt(2),rs.getString(3),rs.getTimestamp(4).toInstant());if(rs.next())throw new SecurityException("ambiguous receipt");return r;}
   }catch(SQLException e){throw new IllegalStateException("idempotency read unavailable",e);}
  }
  @Override public void create(TriageCase c){
   String sql="INSERT INTO synqoria_triage_secure_cases_quarantine (tenant_id,facility_id,patient_id,case_id,revision,encryption_key_id,encrypted_snapshot,state_digest,updated_at) VALUES (?,?,?,?,?,?,?,?,?)";
   try(var ps=db.prepareStatement(sql)){ps.setQueryTimeout(10);scope(ps,of(c),1);ps.setInt(5,c.revision());updated(ps,c,encrypt(c),6);if(ps.executeUpdate()!=1)throw new IllegalStateException("triage intake was not persisted");}
   catch(SQLException e){throw new IllegalStateException("triage creation unavailable",e);}
  }
  @Override public void update(TriageCase before,TriageCase after){
   if(!of(before).equals(of(after))||before.revision()+1!=after.revision())throw new SecurityException("invalid persisted triage transition");
   String sql="UPDATE synqoria_triage_secure_cases_quarantine SET revision=?,encryption_key_id=?,encrypted_snapshot=?,state_digest=?,updated_at=? WHERE tenant_id=? AND facility_id=? AND patient_id=? AND case_id=? AND revision=?";
   try(var ps=db.prepareStatement(sql)){ps.setQueryTimeout(10);ps.setInt(1,after.revision());updated(ps,after,encrypt(after),2);scope(ps,of(before),6);ps.setInt(10,before.revision());if(ps.executeUpdate()!=1)throw new IllegalStateException("triage optimistic revision conflict");}
   catch(SQLException e){throw new IllegalStateException("triage update unavailable",e);}
  }
  @Override public void appendAuditAndOutbox(TriageCase updated,TriageCase.Change event){
   if(event==null||event.revision()!=updated.revision()||!TriageAudit.verify(updated))throw new SecurityException("triage event unavailable");
   if(updated.revision()==1){
    if(updated.genesis()==null || !updated.genesis().equals(event))throw new SecurityException("missing triage arrival proof");
    String state=TriageAudit.stateDigest(updated);
    if(!updated.changes().isEmpty()||!event.operation().equals("ARRIVAL")||!event.previousDigest().equals("0".repeat(64))||!event.stateDigest().equals(state)||
       !event.digest().equals(TriageAudit.eventDigest(updated,event.previousDigest(),state,"ARRIVAL",event.actor(),event.commandId(),event.at(),1)))
       throw new SecurityException("triage genesis event integrity invalid");
   }else if(updated.changes().isEmpty()||!updated.changes().get(updated.changes().size()-1).equals(event))throw new SecurityException("triage event chain mismatch");
   String sql="INSERT INTO synqoria_triage_secure_events_quarantine (tenant_id,facility_id,patient_id,case_id,revision,command_id,actor_digest,operation,previous_digest,state_digest,event_digest,occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
   try(var ps=db.prepareStatement(sql)){ps.setQueryTimeout(10);scope(ps,of(updated),1);ps.setLong(5,event.revision());ps.setObject(6,event.commandId());ps.setString(7,TriageAudit.hash("triage-actor-v1|"+event.actor()));ps.setString(8,event.operation());ps.setString(9,event.previousDigest());ps.setString(10,event.stateDigest());ps.setString(11,event.digest());ps.setTimestamp(12,Timestamp.from(event.at()));if(ps.executeUpdate()!=1)throw new IllegalStateException("triage audit not appended");}
   catch(SQLException e){throw new IllegalStateException("triage audit write unavailable",e);}
   String outbox="INSERT INTO synqoria_triage_secure_outbox_quarantine (event_id,tenant_id,facility_id,patient_id,case_id,command_id,event_digest,event_type,created_at) VALUES (?,?,?,?,?,?,?,?,?)";
   try(var ps=db.prepareStatement(outbox)){ps.setQueryTimeout(10);ps.setObject(1,UUID.randomUUID());scope(ps,of(updated),2);ps.setObject(6,event.commandId());ps.setString(7,event.digest());ps.setString(8,event.operation());ps.setTimestamp(9,Timestamp.from(event.at()));if(ps.executeUpdate()!=1)throw new IllegalStateException("triage outbox not appended");}
   catch(SQLException e){throw new IllegalStateException("triage outbox unavailable",e);}
  }
  @Override public void saveReceipt(TriagePersistencePort.Scope sc,Receipt receipt){
   String sql="INSERT INTO synqoria_triage_secure_receipts_quarantine (tenant_id,facility_id,patient_id,case_id,command_id,request_digest,revision,event_digest,committed_at) VALUES (?,?,?,?,?,?,?,?,?)";
   try(var ps=db.prepareStatement(sql)){ps.setQueryTimeout(10);scope(ps,sc,1);ps.setObject(5,receipt.commandId());ps.setString(6,receipt.fingerprint());ps.setInt(7,receipt.revision());ps.setString(8,receipt.eventDigest());ps.setTimestamp(9,Timestamp.from(receipt.committedAt()));if(ps.executeUpdate()!=1)throw new IllegalStateException("triage idempotency receipt not persisted");}
   catch(SQLException e){throw new IllegalStateException("triage receipt unavailable",e);}
  }
 }
}
