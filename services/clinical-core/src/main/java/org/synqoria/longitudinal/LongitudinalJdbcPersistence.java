package org.synqoria.longitudinal;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import javax.crypto.SecretKey;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Quarantined JDBC adapter for V020 schema. Production role has NO grants until reviewed deployment. */
public final class LongitudinalJdbcPersistence implements Persistence {
 private final DataSource source;
 private final LongitudinalEnvelopeCrypto.KeyResolver keys;
 public LongitudinalJdbcPersistence(DataSource source,LongitudinalEnvelopeCrypto.KeyResolver keys){
  this.source=Objects.requireNonNull(source);this.keys=Objects.requireNonNull(keys);
 }
 private static void bind(PreparedStatement p,Scope s,int n)throws SQLException {
  p.setObject(n,s.tenant());p.setObject(n+1,s.facility());p.setObject(n+2,s.patient());p.setObject(n+3,s.encounter());
 }
 private static void one(int updated,String problem){if(updated!=1)throw new IllegalStateException(problem);}
 @Override public <T> T transaction(Scope scope,Function<Persistence.Tx,T> action){
  Objects.requireNonNull(scope);Objects.requireNonNull(action);
  try(Connection db=source.getConnection()){
   if(!db.getAutoCommit())throw new SQLException("unmanaged transaction refused","42501");
   db.setAutoCommit(false);
   try{
    db.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    try(Statement limits=db.createStatement()){
     limits.execute("SET LOCAL lock_timeout = '3000ms'");
     limits.execute("SET LOCAL statement_timeout = '10000ms'");
     limits.execute("SET LOCAL idle_in_transaction_session_timeout = '12000ms'");
    }
    LongitudinalJdbcScope.install(db,scope);
    T value=action.apply(new JdbcTx(db,scope));
    db.commit();return value;
   }catch(Throwable error){try{db.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);}
    if(error instanceof Error serious)throw serious;
    if(error instanceof RuntimeException runtime)throw runtime;
    throw new IllegalStateException("clinical transaction unavailable",error);
   }
  }catch(SQLException e){throw new IllegalStateException("clinical persistence unavailable",e);}
 }
 private final class JdbcTx implements Persistence.Tx {
  private final Connection db;private final Scope scope;
  private State loaded;private boolean loadedOnce=false,written=false;
  JdbcTx(Connection db,Scope scope){this.db=db;this.scope=scope;}
  private LongitudinalEnvelopeCrypto.Envelope encrypt(State s){
   String keyId=keys.activeId();SecretKey key=keys.activeKey();
   byte[] bytes=LongitudinalSnapshotCodec.encode(s);
   try{return LongitudinalEnvelopeCrypto.seal(new LongitudinalEnvelopeCrypto.Context(scope,s.revision,keyId),bytes,key);}
   finally{Arrays.fill(bytes,(byte)0);}
  }
  @Override public State loadLocked(){
   if(loadedOnce)throw new IllegalStateException("aggregate already loaded");loadedOnce=true;
   String sql="SELECT revision,key_id,encrypted_snapshot,audit_head FROM synqoria_longitudinal_cases_quarantine "+
    "WHERE tenant_id=? AND facility_id=? AND patient_id=? AND encounter_id=? FOR UPDATE";
   try(PreparedStatement p=db.prepareStatement(sql)){
    p.setQueryTimeout(10);bind(p,scope,1);
    try(ResultSet rows=p.executeQuery()){
     if(!rows.next())return null;
     long revision=rows.getLong(1);String keyId=rows.getString(2);byte[] encrypted=rows.getBytes(3),head=rows.getString(4)==null?null:rows.getString(4).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
     if(revision<0||revision>20000||encrypted==null||keyId==null||head==null)throw new SecurityException("clinical aggregate unavailable");
     var envelope=new LongitudinalEnvelopeCrypto.Envelope(keyId,encrypted);
     byte[] decoded=LongitudinalEnvelopeCrypto.open(new LongitudinalEnvelopeCrypto.Context(scope,revision,keyId),envelope,keys.resolve(keyId));
     State s;try{s=LongitudinalSnapshotCodec.decode(decoded);}finally{Arrays.fill(decoded,(byte)0);}
     if(!scope.equals(s.scope)||revision!=s.revision||!head(s).equals(new String(head,java.nio.charset.StandardCharsets.US_ASCII)))
      throw new SecurityException("clinical scope/digest mismatch");
     if(rows.next())throw new SecurityException("ambiguous aggregate rows");
     verifyLedger(s);
     loaded=new State(s);
     return new State(s);
    }
   }catch(SQLException e){throw new IllegalStateException("scoped case unavailable",e);}
  }
  private void verifyLedger(State s){
   String sql="SELECT e.revision,e.command_id,e.action,e.actor_id,e.occurred_at,e.previous_hash,e.state_digest,e.event_hash,"+
    "r.fingerprint,r.event_hash,o.event_hash FROM synqoria_longitudinal_events_quarantine e "+
    "LEFT JOIN synqoria_longitudinal_receipts_quarantine r ON r.tenant_id=e.tenant_id AND r.facility_id=e.facility_id "+
    "AND r.patient_id=e.patient_id AND r.encounter_id=e.encounter_id AND r.command_id=e.command_id AND r.revision=e.revision "+
    "LEFT JOIN synqoria_longitudinal_outbox_quarantine o ON o.tenant_id=e.tenant_id AND o.facility_id=e.facility_id "+
    "AND o.patient_id=e.patient_id AND o.encounter_id=e.encounter_id AND o.command_id=e.command_id AND o.revision=e.revision "+
    "WHERE e.tenant_id=? AND e.facility_id=? AND e.patient_id=? AND e.encounter_id=? ORDER BY e.revision";
   try(PreparedStatement p=db.prepareStatement(sql)){
    p.setQueryTimeout(10);bind(p,scope,1);try(ResultSet rs=p.executeQuery()){
     int position=0;
     while(rs.next()){
      if(position>=s.events.size())throw new SecurityException("unexpected persisted clinical event");
      Event e=s.events.get(position++);Receipt r=s.receipts.get(e.commandId());
      Timestamp at=rs.getTimestamp(5);
      if(at==null||r==null||rs.getLong(1)!=e.revision()||!e.commandId().equals(rs.getObject(2))||
        !e.action().equals(rs.getString(3))||!e.actor().equals(rs.getObject(4))||!e.at().equals(at.toInstant())||
        !e.previousHash().equals(rs.getString(6))||!e.stateDigest().equals(rs.getString(7))||
        !e.hash().equals(rs.getString(8))||!r.fingerprint().equals(rs.getString(9))||
        !e.hash().equals(rs.getString(10))||!e.hash().equals(rs.getString(11)))
        throw new SecurityException("clinical evidence integrity mismatch");
     }
     if(position!=s.events.size())throw new SecurityException("clinical evidence truncated");
    }
   }catch(SQLException e){throw new SecurityException("clinical evidence unavailable",e);}
  }
  @Override public void saveAtomically(State next,Event event,Receipt receipt){
   if(written||!loadedOnce||loaded==null||next==null||event==null||receipt==null||
      !scope.equals(next.scope)||!scope.equals(loaded.scope)||next.revision!=loaded.revision+1||
      !auditValid(next)||!next.events.get(next.events.size()-1).equals(event)||
      !next.outbox.get(next.outbox.size()-1).equals(event)||
      !receipt.equals(next.receipts.get(receipt.commandId()))||
      !event.commandId().equals(receipt.commandId())||!event.hash().equals(receipt.eventHash()))
       throw new SecurityException("invalid atomic clinical evidence");
   var sealed=encrypt(next);
   String sql="UPDATE synqoria_longitudinal_cases_quarantine SET revision=?,key_id=?,encrypted_snapshot=?,audit_head=? "+
    "WHERE tenant_id=? AND facility_id=? AND patient_id=? AND encounter_id=? AND revision=?";
   try(PreparedStatement p=db.prepareStatement(sql)){
    p.setQueryTimeout(10);p.setLong(1,next.revision);p.setString(2,sealed.keyId());p.setBytes(3,sealed.value());p.setString(4,head(next));bind(p,scope,5);p.setLong(9,loaded.revision);
    one(p.executeUpdate(),"clinical revision conflict");
   }catch(SQLException e){throw new IllegalStateException("clinical aggregate write failed",e);}
   String eventSql="INSERT INTO synqoria_longitudinal_events_quarantine (tenant_id,facility_id,patient_id,encounter_id,revision,command_id,actor_id,action,occurred_at,previous_hash,state_digest,event_hash) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
   try(PreparedStatement p=db.prepareStatement(eventSql)){
    p.setQueryTimeout(10);bind(p,scope,1);p.setLong(5,event.revision());p.setObject(6,event.commandId());p.setObject(7,event.actor());
    p.setString(8,event.action());p.setTimestamp(9,Timestamp.from(event.at()));p.setString(10,event.previousHash());p.setString(11,event.stateDigest());p.setString(12,event.hash());one(p.executeUpdate(),"clinical audit write incomplete");
   }catch(SQLException e){throw new IllegalStateException("clinical audit write failed",e);}
   String receiptSql="INSERT INTO synqoria_longitudinal_receipts_quarantine (tenant_id,facility_id,patient_id,encounter_id,command_id,revision,fingerprint,event_hash) VALUES (?,?,?,?,?,?,?,?)";
   try(PreparedStatement p=db.prepareStatement(receiptSql)){
    p.setQueryTimeout(10);bind(p,scope,1);p.setObject(5,receipt.commandId());p.setLong(6,receipt.revision());p.setString(7,receipt.fingerprint());p.setString(8,receipt.eventHash());one(p.executeUpdate(),"clinical receipt write incomplete");
   }catch(SQLException e){throw new IllegalStateException("clinical receipt write failed",e);}
   String outboxSql="INSERT INTO synqoria_longitudinal_outbox_quarantine (tenant_id,facility_id,patient_id,encounter_id,command_id,revision,event_hash,event_type) VALUES (?,?,?,?,?,?,?,?)";
   try(PreparedStatement p=db.prepareStatement(outboxSql)){
    p.setQueryTimeout(10);bind(p,scope,1);p.setObject(5,event.commandId());p.setLong(6,event.revision());p.setString(7,event.hash());p.setString(8,event.action());one(p.executeUpdate(),"clinical outbox write incomplete");
   }catch(SQLException e){throw new IllegalStateException("clinical outbox write failed",e);}
   written=true;
  }
 }
 private static String head(State s){return s.events.isEmpty()?"0".repeat(64):s.events.get(s.events.size()-1).hash();}
}
