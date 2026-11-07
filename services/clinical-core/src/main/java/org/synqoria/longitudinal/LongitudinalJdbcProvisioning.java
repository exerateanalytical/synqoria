package org.synqoria.longitudinal;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.synqoria.security.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Server-only planned-case initialization. Not exposed through HTTP and not a patient onboarding service. */
public final class LongitudinalJdbcProvisioning {
 private final DataSource source;private final LongitudinalEnvelopeCrypto.KeyResolver keys;
 public LongitudinalJdbcProvisioning(DataSource source,LongitudinalEnvelopeCrypto.KeyResolver keys){
  this.source=Objects.requireNonNull(source);this.keys=Objects.requireNonNull(keys);
 }
 public void initialize(TrustedAccessContext identity,TrustedClinicalAccessEvidence evidence,Scope scope,Instant serverNow){
  if(scope==null||identity==null||evidence==null||serverNow==null||
    ClinicalAccessPolicy.authorize(identity,evidence,scope.tenant(),scope.facility(),scope.patient(),
      "encounter.provision",serverNow)!=ClinicalAccessPolicy.Decision.ALLOWED)
      throw new SecurityException("clinical resource unavailable");
  State empty=new State(scope);byte[] plain=LongitudinalSnapshotCodec.encode(empty);
  try(Connection db=source.getConnection()){
   if(!db.getAutoCommit())throw new SQLException("unmanaged transaction refused","42501");
   db.setAutoCommit(false);
   try{
    db.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    try(Statement timeouts=db.createStatement()){
     timeouts.execute("SET LOCAL lock_timeout = '3000ms'");
     timeouts.execute("SET LOCAL statement_timeout = '10000ms'");
    }
    LongitudinalJdbcScope.install(db,scope);
    String keyId=keys.activeId();var encrypted=LongitudinalEnvelopeCrypto.seal(
       new LongitudinalEnvelopeCrypto.Context(scope,0,keyId),plain,keys.activeKey());
    String sql="INSERT INTO synqoria_longitudinal_cases_quarantine "+
     "(tenant_id,facility_id,patient_id,encounter_id,revision,key_id,encrypted_snapshot,audit_head) "+
     "VALUES (?,?,?,?,0,?,?,?) ON CONFLICT DO NOTHING";
    try(PreparedStatement p=db.prepareStatement(sql)){
     p.setQueryTimeout(10);p.setObject(1,scope.tenant());p.setObject(2,scope.facility());
     p.setObject(3,scope.patient());p.setObject(4,scope.encounter());
     p.setString(5,keyId);p.setBytes(6,encrypted.value());p.setString(7,"0".repeat(64));
     if(p.executeUpdate()!=1)throw new IllegalStateException("planned encounter already exists or was denied");
    }
    db.commit();
   }catch(Throwable failure){try{db.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}
    if(failure instanceof Error e)throw e;if(failure instanceof RuntimeException r)throw r;
    throw new IllegalStateException("clinical provisioning unavailable",failure);
   }
  }catch(SQLException ex){throw new IllegalStateException("clinical provisioning unavailable",ex);}
  finally{Arrays.fill(plain,(byte)0);}
 }
}
