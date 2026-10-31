package org.synqoria.security;
import java.time.Instant;import java.util.*;
/** P03-B0007: verified purpose/operation evidence and auditable decision obligation.
 * Evidence must be sourced from an approved server-side grant provider, not a browser claim. */
public final class PurposeOfUseDecision {
 private PurposeOfUseDecision(){}
 public record Evidence(UUID tenant,UUID actor,UUID patient,String purpose,Set<String> operations,
                        Instant issued,Instant expires,String auditReference,boolean verifiedByTrustedGrantProvider){
  public Evidence {Objects.requireNonNull(operations);operations=Set.copyOf(operations);}
 }
 public record Decision(boolean allowed,boolean auditRequired,String reason){}
 public static Decision evaluate(TrustedAccessContext caller,UUID patient,String purpose,String operation,Evidence evidence,Instant now){
  var denied=new Decision(false,true,"NOT_AUTHORIZED");
  if(caller==null||patient==null||purpose==null||operation==null||evidence==null||now==null)return denied;
  if(!evidence.verifiedByTrustedGrantProvider() || !caller.tenantId().equals(evidence.tenant())
      ||!caller.actorId().equals(evidence.actor())||!patient.equals(evidence.patient())
      ||!caller.permittedPatientIds().contains(patient)||!caller.capabilities().contains(operation))return denied;
  if(!Set.of("TREATMENT","OPERATIONS","PATIENT_ACCESS").contains(purpose)||!purpose.equals(evidence.purpose())
      ||!evidence.operations().contains(operation)||evidence.auditReference()==null
      ||!evidence.auditReference().matches("[A-Za-z0-9_-]{12,96}"))return denied;
  if(evidence.issued()==null||evidence.expires()==null||now.isBefore(evidence.issued())
     ||!now.isBefore(evidence.expires())||evidence.expires().isAfter(evidence.issued().plusSeconds(900)))return denied;
  return new Decision(true,true,"ALLOW_WITH_AUDIT");
 }
}
