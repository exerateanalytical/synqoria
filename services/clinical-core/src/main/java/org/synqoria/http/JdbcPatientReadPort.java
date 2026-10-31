package org.synqoria.http;
import org.synqoria.security.*;
import org.synqoria.persistence.TransactionalPatientReadJdbc;
import java.time.Instant;
import java.util.*;
/** Transaction and durable-audit adapter; connection source MUST provide configured restricted app role. */
public final class JdbcPatientReadPort implements PatientReadPort {
 private final TransactionalPatientReadJdbc service;
 public JdbcPatientReadPort(TransactionalPatientReadJdbc service){this.service=Objects.requireNonNull(service);}
 @Override public AuditedPatientAccess.Result read(TrustedAccessContext actor,UUID id,UUID correlation,Instant at){
  if(actor==null||id==null||correlation==null||at==null)return new AuditedPatientAccess.Result(AuditedPatientAccess.Status.NOT_DISCLOSED,null);
  try{
   var result=service.read(actor,id,correlation,at);
   return switch(result.status()){
    case OK->new AuditedPatientAccess.Result(AuditedPatientAccess.Status.OK,result.patient());
    case NOT_DISCLOSED->new AuditedPatientAccess.Result(AuditedPatientAccess.Status.NOT_DISCLOSED,null);
    case UNAVAILABLE->new AuditedPatientAccess.Result(AuditedPatientAccess.Status.UNAVAILABLE,null);
   };
  }catch(RuntimeException e){return new AuditedPatientAccess.Result(AuditedPatientAccess.Status.UNAVAILABLE,null);}
 }
}
