package org.synqoria.http;
import org.synqoria.security.*;
import java.time.Instant;
import java.util.*;
/** Append-only sink must be durable before an authorized payload can leave the backend. */
public final class AuditedPatientAccess {
 @FunctionalInterface public interface AuditSink {boolean commit(UUID actor,UUID tenant,UUID facility,UUID patient,String operation,Instant occurredAt);}
 public enum Status {OK,NOT_DISCLOSED,UNAVAILABLE}
 public record Result(Status status,PatientRecordEnvelope patient) {public Result {if((status==Status.OK)!=(patient!=null))throw new IllegalArgumentException("patient disclosure integrity");}}
 private final PatientReadService service;private final AuditSink audit;
 public AuditedPatientAccess(PatientReadService service,AuditSink audit){this.service=Objects.requireNonNull(service);this.audit=Objects.requireNonNull(audit);}
 public Result read(TrustedAccessContext caller,UUID id,Instant now){
  var outcome=service.read(caller,id,now);
  if(outcome.status()==PatientReadService.Status.SERVICE_UNAVAILABLE)return new Result(Status.UNAVAILABLE,null);
  if(outcome.status()!=PatientReadService.Status.OK)return new Result(Status.NOT_DISCLOSED,null);
  try {
   if(!audit.commit(caller.actorId(),caller.tenantId(),caller.facilityId(),id,"patient:read",now))return new Result(Status.UNAVAILABLE,null);
  } catch(RuntimeException e){return new Result(Status.UNAVAILABLE,null);}
  return new Result(Status.OK,outcome.record());
 }
}
